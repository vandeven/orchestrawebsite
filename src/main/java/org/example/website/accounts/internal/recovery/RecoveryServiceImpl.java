package org.example.website.accounts.internal.recovery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.api.recovery.RecoveryLinkInvalid;
import org.example.website.accounts.api.recovery.RecoveryService;
import org.example.website.accounts.api.rules.AccountsRules;
import org.example.website.accounts.api.rules.PasswordPolicy;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserService;
import org.example.website.accounts.internal.config.AccountsConfig;
import org.example.website.accounts.internal.security.Fingerprints;
import org.example.website.accounts.internal.security.SessionTokens;
import org.example.website.accounts.internal.store.RecoveryTokenRecord;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.mail.Email;
import org.example.website.platform.api.mail.EmailLayout;
import org.example.website.platform.api.mail.Mailer;
import org.example.website.platform.api.support.LogSafe;
import org.jboss.logging.Logger;

@ApplicationScoped
class RecoveryServiceImpl implements RecoveryService {

    private static final Logger LOG = Logger.getLogger(RecoveryServiceImpl.class);

    /** Recovery emails per address per hour. More are silently not sent (USR-22). */
    static final int EMAILS_PER_ADDRESS = 3;

    /** Recovery requests per visitor per hour. More get HTTP 429 (USR-22). */
    static final int REQUESTS_PER_VISITOR = 30;

    /** Wrong recovery links per visitor per hour. More get HTTP 429 (LOG-13). */
    static final int WRONG_LINKS_PER_VISITOR = 20;

    static final Duration PERIOD = Duration.ofHours(1);

    @Inject UserRepository users;
    @Inject RecoveryTokenRepository tokens;
    @Inject SessionRepository sessions;
    @Inject UserService userService;
    @Inject PasswordPolicy policy;
    @Inject Mailer mailer;
    @Inject RequestCounter counter;
    @Inject Fingerprints fingerprints;
    @Inject PlatformConfig platform;
    @Inject AccountsConfig config;
    @Inject Clock clock;

    @Override
    public void request(String email, String clientAddress) {
        long start = System.nanoTime();
        try {
            String normalized = AccountsRules.normalizeEmail(email);
            if (clientAddress != null) {
                var visitor =
                        counter.hit(
                                "recovery-visitor:" + fingerprints.address(clientAddress), PERIOD);
                if (visitor.count() > REQUESTS_PER_VISITOR) {
                    LOG.warn("Recovery requests refused: one visitor asked too often");
                    throw new TooManyRequests(
                            "Too many recovery requests", visitor.untilReset().toSeconds());
                }
            }
            if (!AccountsRules.validEmail(normalized)) {
                LOG.info("Recovery request: no email sent (not a valid address)");
                return;
            }
            // Counted for every address, also unknown ones, so the limit shows nothing about
            // accounts
            var perAddress =
                    counter.hit("recovery-account:" + fingerprints.email(normalized), PERIOD);
            if (perAddress.count() > EMAILS_PER_ADDRESS) {
                LOG.warn("Recovery email not sent: the limit per address was reached");
                return;
            }
            Optional<UserRecord> user = users.findByEmail(normalized);
            if (user.isPresent() && user.get().role() != Role.MASTER) { // USR-16
                try {
                    sendTo(user.get().id());
                } catch (ServiceUnavailable e) {
                    // Logged and alerted in sendTo. The visitor sees the same message (USR-18).
                }
            } else {
                // Only the log tells the owner; the visitor sees the same page in every case
                LOG.info(
                        "Recovery request: no email sent (no account for the address, or the"
                                + " master account)");
            }
        } finally {
            Pacer.waitUntil(start, config.recoveryMinResponse());
        }
    }

    @Override
    public void sendTo(UUID userId) {
        UserRecord user =
                users.findById(userId).orElseThrow(() -> new ResourceNotFound("No such user"));
        if (user.role() == Role.MASTER) {
            throw new InvalidInput("No recovery for the master account"); // USR-6, USR-16
        }
        Instant now = clock.instant();
        String token = SessionTokens.newToken(); // 256 bits (USR-19)
        String tokenHash = SessionTokens.hash(token);
        tokens.deleteForUser(userId); // an older link stops working (USR-19)
        tokens.save(new RecoveryTokenRecord(tokenHash, userId, now.plus(LINK_LIFETIME)));

        // Built from the configured address, never from the request (HRD-1)
        String link = platform.publicBaseUrl() + "/reset-password?token=" + token;
        try {
            mailer.send(recoveryEmail(user.email(), link));
        } catch (ServiceUnavailable e) {
            tokens.deleteByHash(tokenHash); // nobody got the link
            LOG.error("ALERT: a password recovery email could not be sent");
            throw e;
        }
        users.update(users.findById(userId).orElse(user).withRecoveryEmailSentAt(now)); // USR-24
        LOG.infof("Recovery email sent (address %s)", LogSafe.hash(user.email()));
    }

    private Email recoveryEmail(String to, String link) {
        String name = platform.name();
        String heading = "Choose your password";
        List<String> paragraphs =
                List.of(
                        "Hello,",
                        "An account was created for this email address on the website of "
                                + name
                                + ", or someone asked to reset the password. Use the button below to choose"
                                + " your password.",
                        "The link works once and for 1 hour. After that you can ask for a new one on the log in page.",
                        "If you did not ask for this, you can ignore this email. Nothing changes until"
                                + " someone uses the link.");
        String footer =
                "You received this email because of a request on the website of " + name + ".";
        return new Email(
                to,
                "Choose your password for " + name,
                EmailLayout.text(name, heading, paragraphs, "Choose your password", link, footer),
                EmailLayout.html(name, heading, paragraphs, "Choose your password", link, footer));
    }

    @Override
    public boolean isValid(String token, String clientAddress) {
        boolean valid = findValid(token).isPresent();
        if (!valid) {
            recordWrongLink(clientAddress);
        }
        return valid;
    }

    @Override
    public void complete(String token, String newPassword, String clientAddress) {
        Optional<RecoveryTokenRecord> record = findValid(token);
        if (record.isEmpty()) {
            recordWrongLink(clientAddress);
            throw new RecoveryLinkInvalid();
        }
        if (policy.problem(newPassword).isPresent()) {
            throw new InvalidInput("Password does not follow the rules for new passwords");
        }
        UUID userId = record.get().userId();
        userService.setPassword(userId, newPassword); // also ends all sessions (USR-21)
        tokens.deleteForUser(userId); // the link works only once (USR-19)
        sessions.deleteAllForUser(userId);
        LOG.info("Security event: a password was set through a recovery link");
    }

    /**
     * The stored link when the token is genuine, not expired and its user still exists and is not
     * the master.
     */
    private Optional<RecoveryTokenRecord> findValid(String token) {
        if (!SessionTokens.looksValid(token)) {
            return Optional.empty();
        }
        String hash = SessionTokens.hash(token);
        Optional<RecoveryTokenRecord> record = tokens.findByHash(hash);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        if (!record.get().expiresAt().isAfter(clock.instant())) {
            tokens.deleteByHash(hash);
            return Optional.empty();
        }
        Optional<UserRecord> user = users.findById(record.get().userId());
        if (user.isEmpty() || user.get().role() == Role.MASTER) {
            tokens.deleteByHash(hash);
            return Optional.empty();
        }
        return record;
    }

    private void recordWrongLink(String clientAddress) {
        LOG.warn("Security event: an invalid or expired recovery link was used");
        if (clientAddress == null) {
            return;
        }
        var visitor = counter.hit("recovery-link:" + fingerprints.address(clientAddress), PERIOD);
        if (visitor.count() > WRONG_LINKS_PER_VISITOR) {
            LOG.warn("Recovery links refused: one visitor used too many wrong links");
            throw new TooManyRequests(
                    "Too many wrong recovery links", visitor.untilReset().toSeconds());
        }
    }
}
