package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.api.LogSafe;
import org.jboss.logging.Logger;

@ApplicationScoped
class LoginServiceImpl implements LoginService {

    private static final Logger LOG = Logger.getLogger(LoginServiceImpl.class);

    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject PasswordHasher hasher;
    @Inject Clock clock;
    @Inject LoginThrottle throttle;

    /** A hash of a random password, checked when there is nothing real to check (LOG-13). */
    private volatile String dummyHash;

    @Override
    public LoginResult login(String email, String password, String clientAddress) {
        String normalized = AccountsRules.normalizeEmail(email);
        // Too many failures: refused before anything is looked up or hashed (LOG-13)
        var wait = throttle.waitTime(normalized, clientAddress);
        if (wait.isPresent()) {
            LOG.warnf(
                    "Login refused: too many failed attempts (address %s)",
                    LogSafe.hash(normalized));
            return new LoginResult.Throttled(wait.get());
        }
        boolean wellFormed =
                AccountsRules.validEmail(normalized) && password != null && !password.isEmpty();
        // The same password check runs for an unknown address, an account without a password and a
        // malformed request, so the response time tells nothing about which accounts exist.
        UserRecord user = wellFormed ? users.findByEmail(normalized).orElse(null) : null;
        boolean hasPassword = user != null && user.passwordHash() != null;
        String candidate = password == null ? "" : cut(password);
        boolean passwordMatches =
                hasher.verify(candidate, hasPassword ? user.passwordHash() : dummyHash());

        if (!wellFormed || !hasPassword || !passwordMatches) {
            LOG.warnf("Failed login (address %s)", LogSafe.hash(normalized));
            throttle.recordFailure(normalized, clientAddress);
            return new LoginResult.Failed();
        }

        UserRecord updated = user;
        if (hasher.needsRehash(user.passwordHash())) {
            updated = updated.withPasswordHash(hasher.hash(candidate)); // LOG-20
        }
        Instant now = clock.instant();
        updated = updated.withLastLogin(now);
        users.update(updated);

        throttle.recordSuccess(normalized, clientAddress);

        // Every login gets a new token, so a token from before the login is never valid (no
        // fixation)
        String token = SessionTokens.newToken();
        sessions.save(
                new SessionRecord(
                        SessionTokens.hash(token), updated.id(), now.plus(SESSION_LIFETIME)));
        return new LoginResult.LoggedIn(token, updated.summary());
    }

    @Override
    public Optional<UserSummary> userForSession(String sessionToken) {
        if (!SessionTokens.looksValid(sessionToken)) {
            return Optional.empty();
        }
        String tokenHash = SessionTokens.hash(sessionToken);
        Optional<SessionRecord> session = sessions.findByTokenHash(tokenHash);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (!session.get().expiresAt().isAfter(clock.instant())) { // LOG-10
            sessions.delete(tokenHash);
            return Optional.empty();
        }
        // The user is read on every call, so a removed user or a changed role counts at once
        // (LOG-11)
        Optional<UserRecord> user = users.findById(session.get().userId());
        if (user.isEmpty()) {
            sessions.delete(tokenHash);
            return Optional.empty();
        }
        return Optional.of(user.get().summary());
    }

    @Override
    public void logout(String sessionToken) {
        if (SessionTokens.looksValid(sessionToken)) {
            sessions.delete(SessionTokens.hash(sessionToken));
        }
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = hasher.hash(SessionTokens.newToken());
            dummyHash = hash;
        }
        return hash;
    }

    private static String cut(String password) {
        return password.length() > AccountsRules.PASSWORD_MAX
                ? password.substring(0, AccountsRules.PASSWORD_MAX)
                : password;
    }
}
