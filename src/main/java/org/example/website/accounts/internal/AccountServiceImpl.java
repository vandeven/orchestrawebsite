package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.example.website.accounts.api.AccountService;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.CurrentPasswordIncorrect;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.PasswordPolicy;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.api.AccessDenied;
import org.example.website.platform.api.AmsterdamTime;
import org.example.website.platform.api.Email;
import org.example.website.platform.api.EmailLayout;
import org.example.website.platform.api.InvalidInput;
import org.example.website.platform.api.LogSafe;
import org.example.website.platform.api.Mailer;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.ServiceUnavailable;
import org.example.website.platform.api.TooManyRequests;
import org.jboss.logging.Logger;

@ApplicationScoped
class AccountServiceImpl implements AccountService {

    private static final Logger LOG = Logger.getLogger(AccountServiceImpl.class);

    @Inject LoginService loginService;
    @Inject UserService userService;
    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject PasswordHasher hasher;
    @Inject PasswordPolicy policy;
    @Inject LoginThrottle throttle;
    @Inject Mailer mailer;
    @Inject PlatformConfig platform;
    @Inject Clock clock;

    @Override
    public String changePassword(
            String sessionToken, String currentPassword, String newPassword, String clientAddress) {
        UserSummary user =
                loginService
                        .userForSession(sessionToken)
                        .orElseThrow(() -> new AccessDenied("Not logged in"));
        if (user.role() == Role.MASTER) {
            // The master password only changes through MASTER_PASSWORD (ACC-8, USR-16)
            throw new AccessDenied("The master password is not changed on the website");
        }
        UserRecord record =
                users.findById(user.id()).orElseThrow(() -> new AccessDenied("Unknown user"));

        // The same limits as logins (ACC-4): a wrong current password is a failed attempt
        var wait = throttle.waitTime(user.email(), clientAddress);
        if (wait.isPresent()) {
            LOG.warn("Password change refused: too many failed attempts");
            throw new TooManyRequests("Too many attempts", (wait.get().toMillis() + 999) / 1000);
        }
        boolean correct =
                currentPassword != null
                        && !currentPassword.isEmpty()
                        && currentPassword.length() <= AccountsRules.PASSWORD_MAX
                        && record.passwordHash() != null
                        && hasher.verify(currentPassword, record.passwordHash());
        if (!correct) {
            throttle.recordFailure(user.email(), clientAddress);
            LOG.warnf(
                    "Security event: wrong current password when changing a password (address %s)",
                    LogSafe.hash(user.email()));
            throw new CurrentPasswordIncorrect();
        }
        throttle.recordSuccess(user.email(), clientAddress);

        if (policy.problem(newPassword).isPresent() || newPassword.equals(currentPassword)) {
            throw new InvalidInput("The new password does not follow the rules");
        }

        // The new session lasts as long as the old one would have: a password change does not
        // extend a login (LOG-10)
        Instant expires =
                sessions.findByTokenHash(SessionTokens.hash(sessionToken))
                        .orElseThrow()
                        .expiresAt();
        userService.setPassword(user.id(), newPassword); // ends all sessions, also this one (ACC-6)
        String newToken = SessionTokens.newToken();
        sessions.save(new SessionRecord(SessionTokens.hash(newToken), user.id(), expires));
        LOG.infof(
                "Security event: a password was changed (address %s)", LogSafe.hash(user.email()));

        notifyUser(user.email());
        return newToken;
    }

    /** ACC-7: a failed email does not undo the change; it is logged. */
    private void notifyUser(String address) {
        try {
            mailer.send(changedEmail(address));
        } catch (ServiceUnavailable e) {
            LOG.error("ALERT: the email about a changed password could not be sent");
        }
    }

    private Email changedEmail(String to) {
        String name = platform.name();
        String heading = "Your password was changed";
        List<String> paragraphs =
                List.of(
                        "Hello,",
                        "The password of your account on the website of "
                                + name
                                + " was changed on "
                                + AmsterdamTime.dateTime(clock.instant())
                                + " (Amsterdam time).",
                        "If this was you, you do not have to do anything.",
                        "If it was not you, contact an admin of the orchestra at once. An admin can send you an"
                                + " email to choose a new password.");
        String footer = "You received this email because the password of your account changed.";
        return new Email(
                to,
                "Your password for " + name + " was changed",
                EmailLayout.text(name, heading, paragraphs, null, null, footer),
                EmailLayout.html(name, heading, paragraphs, null, null, footer));
    }
}
