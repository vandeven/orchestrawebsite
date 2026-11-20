package org.example.website;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.SessionCookie;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;

/** Test only: creates users and real sessions through the public API of accounts. */
@ApplicationScoped
public class TestAccounts {

    public static final String MASTER_EMAIL = "master@example.test";
    public static final String MASTER_PASSWORD = "FAKE-NOT-A-SECRET-test-master-password";
    public static final String PASSWORD = "FAKE-NOT-A-SECRET-correct-horse-battery";

    /** The address of the application under test, as a browser sends it in the Origin header. */
    public static final String ORIGIN = "http://localhost:8081";

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    /** A new visitor address for every call, so failed logins of different tests do not add up. */
    public static String randomAddress() {
        int n = COUNTER.incrementAndGet();
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    private static final java.util.regex.Pattern LINK =
            java.util.regex.Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]{43})");

    @Inject org.example.website.platform.api.mail.Outbox outbox;
    @Inject UserService users;
    @Inject LoginService login;

    /** A new user with a password. */
    public UserSummary newUser(Role role) {
        UserSummary user = users.createUser("user-" + UUID.randomUUID() + "@example.test", role);
        users.setPassword(user.id(), PASSWORD);
        return users.find(user.id()).orElseThrow(); // as stored now, with a password
    }

    /** The session token of a freshly logged in user with the given role. */
    public String sessionToken(Role role) {
        if (role == Role.MASTER) {
            return ((LoginResult.LoggedIn) login.login(MASTER_EMAIL, MASTER_PASSWORD))
                    .sessionToken();
        }
        UserSummary user = newUser(role);
        return ((LoginResult.LoggedIn) login.login(user.email(), PASSWORD)).sessionToken();
    }

    /** The value of the Cookie header for a freshly logged in user with the given role. */
    public String cookie(Role role) {
        return SessionCookie.NAME + "=" + sessionToken(role);
    }

    /** The recovery token in the newest email sent to the address, or null when there is none. */
    public String recoveryToken(String email) {
        var sent = outbox.sent();
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).to().equalsIgnoreCase(email)) {
                var matcher = LINK.matcher(sent.get(i).text());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    /** How many emails were sent to the address. */
    public long emailsTo(String email) {
        return outbox.sent().stream().filter(e -> e.to().equalsIgnoreCase(email)).count();
    }

    /** The ID of the master account. */
    public java.util.UUID masterId() {
        return ((LoginResult.LoggedIn) login.login(MASTER_EMAIL, MASTER_PASSWORD)).user().id();
    }
}
