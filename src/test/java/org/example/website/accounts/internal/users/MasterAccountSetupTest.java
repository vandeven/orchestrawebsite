package org.example.website.accounts.internal.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.internal.TestAccountsConfig;
import org.example.website.accounts.internal.passwords.Argon2PasswordHasher;
import org.example.website.accounts.internal.store.SessionRecord;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.memory.InMemoryStorage;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.junit.jupiter.api.Test;

/** USR-14, USR-15: the master account follows MASTER_EMAIL and MASTER_PASSWORD. */
public class MasterAccountSetupTest {

    private static final String PEPPER = "FAKE-NOT-A-SECRET-pepper-0123456789abcdef-ABCDEF";

    private final TestAccountsConfig config = new TestAccountsConfig();
    private final InMemoryStorage storage =
            new InMemoryStorage(
                    config, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(PEPPER, 4, 3_000);
    private final MasterAccountSetup setup = new MasterAccountSetup();
    private final List<org.example.website.audit.api.AuditEntry> audited =
            new java.util.ArrayList<>();

    MasterAccountSetupTest() {
        setup.users = storage;
        setup.sessions = storage;
        setup.hasher = hasher;
        setup.config = config;
        setup.audit = audited::add;
        setup.clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void createsTheMasterAccountWithThePassword() {
        setup.ensureMasterAccount();
        UserRecord master = storage.findByEmail("master@example.test").orElseThrow();
        assertEquals(Role.MASTER, master.role());
        assertTrue(hasher.verify("FAKE-NOT-A-SECRET-master-one", master.passwordHash()));
        assertFalse(master.passwordHash().contains("FAKE-NOT-A-SECRET"));
    }

    @Test
    void theResultOfTheSetupIsAuditedEveryTime() {
        // LGG-9: the master account setup is part of the audit trail
        setup.ensureMasterAccount();
        setup.ensureMasterAccount();
        config.masterPassword = "FAKE-NOT-A-SECRET-master-two";
        setup.ensureMasterAccount();
        assertEquals(
                List.of("created", "unchanged", "updated"),
                audited.stream().map(a -> a.details().get("outcome")).toList());
        for (var entry : audited) {
            assertEquals("master.setup", entry.action().code());
            assertEquals("success", entry.result().code());
            assertEquals("master@example.test", entry.actor().email());
            assertEquals("MASTER", entry.actor().role());
        }
    }

    @Test
    void runningItAgainChangesNothing() {
        setup.ensureMasterAccount();
        UserRecord first = storage.findByEmail("master@example.test").orElseThrow();
        setup.ensureMasterAccount();
        UserRecord second = storage.findByEmail("master@example.test").orElseThrow();
        assertEquals(first.id(), second.id());
        assertEquals(
                first.passwordHash(), second.passwordHash(), "no new hash for the same password");
        assertEquals(1, storage.findAll().size());
    }

    @Test
    void aChangedPasswordReplacesTheOldOneAndEndsOldSessions() {
        setup.ensureMasterAccount();
        UserRecord master = storage.findByEmail("master@example.test").orElseThrow();
        storage.save(
                new SessionRecord(
                        "hash-of-a-session", master.id(), Instant.parse("2027-01-01T00:00:00Z")));

        config.masterPassword = "FAKE-NOT-A-SECRET-master-two";
        setup.ensureMasterAccount();

        UserRecord updated = storage.findByEmail("master@example.test").orElseThrow();
        assertTrue(hasher.verify("FAKE-NOT-A-SECRET-master-two", updated.passwordHash()));
        assertFalse(hasher.verify("FAKE-NOT-A-SECRET-master-one", updated.passwordHash()));
        assertTrue(storage.findByTokenHash("hash-of-a-session").isEmpty());
    }

    @Test
    void anExistingAccountForTheAddressBecomesTheMaster() {
        UUID id = UUID.randomUUID();
        storage.insertIfEmailFree(
                new UserRecord(
                        id, "master@example.test", Role.MEMBER, null, Instant.EPOCH, null, null));
        setup.ensureMasterAccount();
        UserRecord master = storage.findByEmail("master@example.test").orElseThrow();
        assertEquals(id, master.id(), "the same account, not a new one");
        assertEquals(Role.MASTER, master.role());
        assertNotNull(master.passwordHash());
    }

    @Test
    void thereIsNeverMoreThanOneMaster() {
        setup.ensureMasterAccount();
        config.masterEmail = "new-master@example.test";
        setup.ensureMasterAccount();

        long masters = storage.findAll().stream().filter(u -> u.role() == Role.MASTER).count();
        assertEquals(1, masters);
        assertEquals(Role.ADMIN, storage.findByEmail("master@example.test").orElseThrow().role());
        assertEquals(
                Role.MASTER, storage.findByEmail("new-master@example.test").orElseThrow().role());
    }

    @Test
    void theAddressOfTheMasterIsNormalized() {
        config.masterEmail = "  Master@Example.TEST ";
        setup.ensureMasterAccount();
        assertTrue(storage.findByEmail("master@example.test").isPresent());
    }

    @Test
    void aDemotedMasterWhoIsAlsoAskedForAgainGetsTheRoleBack() {
        setup.ensureMasterAccount();
        UserRecord master = storage.findByEmail("master@example.test").orElseThrow();
        storage.update(master.withRole(Role.MEMBER)); // changed outside the setup
        setup.ensureMasterAccount();
        assertEquals(Role.MASTER, storage.findByEmail("master@example.test").orElseThrow().role());
        assertEquals(
                List.of(Role.MASTER), storage.findAll().stream().map(UserRecord::role).toList());
    }

    @Test
    void inMemoryStorageIsRefusedUnlessItIsSwitchedOn() {
        // A real deployment never uses it: data would be lost and every instance would differ
        config.inMemory = false;
        assertThrows(ServiceUnavailable.class, () -> storage.findByEmail("a@example.test"));
        assertThrows(ServiceUnavailable.class, () -> setup.ensureMasterAccount());
        config.inMemory = true;
        assertTrue(storage.findByEmail("a@example.test").isEmpty());
    }

    @Test
    void theStoreKeepsEmailsUniqueAndCleansUpOnDelete() {
        UserRecord a =
                new UserRecord(
                        UUID.randomUUID(),
                        "a@example.test",
                        Role.MEMBER,
                        null,
                        Instant.EPOCH,
                        null,
                        null);
        UserRecord duplicate =
                new UserRecord(
                        UUID.randomUUID(),
                        "a@example.test",
                        Role.ADMIN,
                        null,
                        Instant.EPOCH,
                        null,
                        null);
        assertTrue(storage.insertIfEmailFree(a));
        assertFalse(storage.insertIfEmailFree(duplicate));
        storage.delete(a.id());
        assertTrue(storage.findByEmail("a@example.test").isEmpty());
        assertTrue(storage.insertIfEmailFree(duplicate), "the address is free again");
    }
}
