package org.example.website.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.api.Conflict;
import org.example.website.platform.api.InvalidInput;
import org.example.website.platform.api.ResourceNotFound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** USR-2 to USR-4, USR-13, USR-21: creating users and setting passwords. */
@QuarkusTest
class UserServiceTest {

    @Inject UserService users;
    @Inject LoginService login;

    private static String address() {
        return "new-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    void aNewUserIsAMemberWithoutAPassword() {
        UserSummary user = users.createUser(address(), Role.MEMBER);
        assertEquals(Role.MEMBER, user.role());
        assertFalse(user.hasPassword(), "USR-4: no password at all");
        assertNull(user.lastLoginAt());
        assertTrue(user.createdAt() != null);
        assertEquals(user, users.find(user.id()).orElseThrow());
    }

    @Test
    void anAdminCanBeCreatedButNeverAMaster() {
        assertEquals(Role.ADMIN, users.createUser(address(), Role.ADMIN).role());
        assertThrows(InvalidInput.class, () -> users.createUser(address(), Role.MASTER)); // USR-13
        assertThrows(InvalidInput.class, () -> users.createUser(address(), null));
    }

    @Test
    void theAddressIsNormalized() {
        String raw = "  Mixed.Case-" + UUID.randomUUID() + "@Example.TEST \n";
        UserSummary user = users.createUser(raw, Role.MEMBER);
        assertEquals(raw.strip().toLowerCase(), user.email());
    }

    @Test
    void anAddressCanOnlyHaveOneAccountWhateverTheCase() {
        String email = address();
        users.createUser(email, Role.MEMBER);
        assertThrows(Conflict.class, () -> users.createUser(email, Role.MEMBER));
        assertThrows(Conflict.class, () -> users.createUser(email.toUpperCase(), Role.ADMIN));
        assertThrows(Conflict.class, () -> users.createUser("  " + email + "  ", Role.MEMBER));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nope", "a@b", "a b@c.de", "a@@c.de", "@c.de", "a@c.de\u0000x"})
    void anInvalidAddressIsRefused(String email) {
        assertThrows(InvalidInput.class, () -> users.createUser(email, Role.MEMBER));
    }

    @Test
    void passwordLengthBoundary() {
        UserSummary user = users.createUser(address(), Role.MEMBER);
        assertThrows(InvalidInput.class, () -> users.setPassword(user.id(), "x".repeat(11)));
        assertThrows(InvalidInput.class, () -> users.setPassword(user.id(), null));
        assertThrows(InvalidInput.class, () -> users.setPassword(user.id(), "x".repeat(129)));
        assertFalse(
                users.find(user.id()).orElseThrow().hasPassword(),
                "a refused password changes nothing");

        users.setPassword(user.id(), "x".repeat(12));
        assertTrue(users.find(user.id()).orElseThrow().hasPassword());
        users.setPassword(user.id(), "y".repeat(128));
        assertTrue(login.login(user.email(), "y".repeat(128)) instanceof LoginResult.LoggedIn);
    }

    @Test
    void settingAPasswordForAnUnknownUserFails() {
        assertThrows(
                ResourceNotFound.class, () -> users.setPassword(UUID.randomUUID(), "x".repeat(12)));
    }

    @Test
    void anAccountCanLogInAfterAPasswordIsSetAndRecordsTheLastLogin() {
        UserSummary user = users.createUser(address(), Role.MEMBER);
        assertTrue(login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed);
        users.setPassword(user.id(), TestAccounts.PASSWORD);
        var result = (LoginResult.LoggedIn) login.login(user.email(), TestAccounts.PASSWORD);
        assertTrue(result.user().lastLoginAt() != null, "USR-7: the last login is kept");
        assertEquals(Role.MEMBER, result.user().role());
    }

    @Test
    void theSummaryNeverHasThePasswordHash() {
        UserSummary user = users.createUser(address(), Role.MEMBER);
        users.setPassword(user.id(), TestAccounts.PASSWORD);
        String text = users.find(user.id()).orElseThrow().toString();
        assertFalse(text.contains("argon2"));
        assertFalse(text.contains(TestAccounts.PASSWORD));
    }
}
