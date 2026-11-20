package org.example.website.accounts.internal.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.login.LoginResult;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.recovery.RecoveryService;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserManagement;
import org.example.website.accounts.api.users.UserManagement.Query;
import org.example.website.accounts.api.users.UserManagement.Sort;
import org.example.website.accounts.api.users.UserManagement.UserRow;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.accounts.internal.security.SessionTokens;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.TestClock;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.Conflict;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.mail.Outbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * USR-1 to USR-13, USR-22 to USR-24 and LGG-9 to LGG-11: the actions, who may do them and what is
 * audited.
 */
@QuarkusTest
class UserManagementTest {

    @Inject UserManagement management;
    @Inject TestAccounts accounts;
    @Inject LoginService login;
    @Inject RecoveryService recovery;
    @Inject Outbox outbox;
    @Inject TestClock clock;
    @Inject UserRepository users;
    @Inject SessionRepository sessionStore;
    @Inject RecoveryTokenRepository recoveryTokenStore;

    private UUID admin;
    private UUID master;
    private UUID memberActor;

    @BeforeEach
    void actors() {
        outbox.clear();
        outbox.failSends(false);
        admin = accounts.newUser(Role.ADMIN).id();
        master = accounts.masterId();
        memberActor = accounts.newUser(Role.MEMBER).id();
    }

    @AfterEach
    void reset() {
        clock.reset();
        outbox.failSends(false);
    }

    private static String newAddress() {
        return "added-" + UUID.randomUUID() + "@example.test";
    }

    private static Map<String, String> only(List<LogRecord> logs) {
        List<Map<String, String>> audits = TestLogs.audits(logs);
        assertEquals(1, audits.size(), "one audit entry per action (LGG-11): " + audits);
        return audits.get(0);
    }

    // Adding users (USR-2 to USR-5)

    @Test
    void anAdminAddsAMemberAndTheRecoveryEmailIsSent() {
        String email = newAddress();
        List<LogRecord> logs =
                TestLogs.capture(
                        () -> assertTrue(management.add(admin, email).recoveryEmailSent()));

        assertEquals(1, accounts.emailsTo(email));
        UserSummary created = users.findByEmail(email).orElseThrow().summary();
        assertEquals(Role.MEMBER, created.role());
        assertFalse(created.hasPassword(), "no password until the link is used (USR-4)");
        assertNotNull(created.recoveryEmailSentAt());

        Map<String, String> audit = only(logs);
        assertEquals("user.add", audit.get("action"));
        assertEquals("success", audit.get("result"));
        assertEquals(admin.toString(), audit.get("actor_id")); // LGG-11: both people
        assertEquals("ADMIN", audit.get("actor_role"));
        assertEquals(email, audit.get("target_email"));
        assertEquals(created.id().toString(), audit.get("target_id"));
        assertEquals("sent", audit.get("detail_recovery_email"));
    }

    @Test
    void theMasterCanAddUsersToo() {
        assertTrue(management.add(master, newAddress()).recoveryEmailSent());
    }

    @Test
    void anExistingAddressIsRefusedWithAClearError() {
        String email = newAddress();
        management.add(admin, email);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                Conflict.class, () -> management.add(admin, email.toUpperCase())));
        assertEquals("denied", only(logs).get("result"));
        assertEquals(1, accounts.emailsTo(email), "no second email");
    }

    @Test
    void anInvalidAddressIsRefused() {
        for (String bad : new String[] {"", "nope", "a@b", "a b@c.de"}) {
            assertThrows(InvalidInput.class, () -> management.add(admin, bad), bad);
        }
    }

    @Test
    void whenTheEmailFailsTheAccountStillExistsAndTheAdminKnows() {
        // USR-24
        outbox.failSends(true);
        String email = newAddress();
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    var result = management.add(admin, email);
                    assertFalse(result.recoveryEmailSent());
                    assertNotNull(result.user().id());
                });
        assertTrue(users.findByEmail(email).isPresent());
        assertTrue(
                logs.stream().anyMatch(r -> TestLogs.everything(r).contains("ALERT")),
                "the failure is logged and alerted");
        assertEquals("failed", only(logs).get("detail_recovery_email"));
        // The admin can send it again once the email service works
        outbox.failSends(false);
        management.resendRecovery(admin, users.findByEmail(email).orElseThrow().id());
        assertEquals(1, accounts.emailsTo(email));
    }

    // Sending the email again (USR-6)

    @Test
    void anAdminCanSendTheEmailAgainToMembersAndAdminsButNotToTheMaster() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        List<LogRecord> logs =
                TestLogs.capture(() -> management.resendRecovery(admin, member.id()));
        management.resendRecovery(admin, otherAdmin.id());
        assertEquals(1, accounts.emailsTo(member.email()));
        assertEquals(1, accounts.emailsTo(otherAdmin.email()));
        assertEquals("user.resend-recovery", only(logs).get("action"));

        List<LogRecord> refused =
                TestLogs.capture(() ->
                        assertThrows(
                                AccessDenied.class,
                                () -> management.resendRecovery(admin, master)));
        assertEquals("denied", only(refused).get("result"));
        assertThrows(AccessDenied.class, () -> management.resendRecovery(master, master));
        assertEquals(0, accounts.emailsTo(TestAccounts.MASTER_EMAIL));
    }

    @Test
    void aFailingEmailServiceGivesTheAdminAnError() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        outbox.failSends(true);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                ServiceUnavailable.class,
                                () -> management.resendRecovery(admin, member.id())));
        assertEquals("failed", only(logs).get("result"));
    }

    // Removing users (USR-9, USR-11, USR-12, USR-13)

    @Test
    void anAdminRemovesAMemberAndTheyLoseAccessAtOnce() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String session =
                ((LoginResult.LoggedIn) login.login(member.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        recovery.sendTo(member.id());
        String link = accounts.recoveryToken(member.email());
        assertTrue(login.userForSession(session).isPresent());

        List<LogRecord> logs = TestLogs.capture(() -> management.remove(admin, member.id()));

        assertTrue(users.findById(member.id()).isEmpty());
        // The stored session and link are removed too, not only ignored when they are used (USR-9)
        assertTrue(
                sessionStore.findByTokenHash(SessionTokens.hash(session)).isEmpty(),
                "the stored session is gone");
        assertTrue(
                recoveryTokenStore.findByHash(SessionTokens.hash(link)).isEmpty(),
                "the stored recovery link is gone");
        assertTrue(login.userForSession(session).isEmpty(), "the session stops working at once");
        assertFalse(
                recovery.isValid(link, TestAccounts.randomAddress()), "so does a recovery link");
        assertTrue(
                login.login(member.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed);

        Map<String, String> audit = only(logs);
        assertEquals("user.remove", audit.get("action"));
        assertEquals("success", audit.get("result"));
        assertEquals(admin.toString(), audit.get("actor_id"));
        assertEquals(member.id().toString(), audit.get("target_id"));
        assertEquals(member.email(), audit.get("target_email"));
    }

    @Test
    void anAdminCannotRemoveAnAdminOrTheMasterAndNothingChanges() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                AccessDenied.class,
                                () -> management.remove(admin, otherAdmin.id())));
        assertEquals(
                "denied",
                only(logs).get("result"),
                "a refused attempt is audited as well (LGG-11)");
        assertThrows(AccessDenied.class, () -> management.remove(admin, master));
        assertThrows(
                AccessDenied.class, () -> management.remove(admin, admin), "not even themselves");
        assertTrue(users.findById(otherAdmin.id()).isPresent());
        assertTrue(users.findById(master).isPresent());
        assertTrue(users.findById(admin).isPresent());
    }

    @Test
    void theMasterCanRemoveAnAdminButNeverTheMaster() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        management.remove(master, otherAdmin.id());
        assertTrue(users.findById(otherAdmin.id()).isEmpty());
        assertThrows(AccessDenied.class, () -> management.remove(master, master));
        assertTrue(users.findById(master).isPresent());
    }

    @Test
    void aRemovedAddressCanBeAddedAgain() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        management.remove(admin, member.id());
        assertTrue(management.add(admin, member.email()).user() != null);
    }

    @Test
    void removingAnUnknownUserIsNotFound() {
        assertThrows(ResourceNotFound.class, () -> management.remove(admin, UUID.randomUUID()));
    }

    // Rights changes (USR-10, USR-12)

    @Test
    void aMemberBecomesAnAdminAtOnceAndBothPeopleAreAudited() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String session =
                ((LoginResult.LoggedIn) login.login(member.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        assertEquals(Role.MEMBER, login.userForSession(session).orElseThrow().role());

        List<LogRecord> logs = TestLogs.capture(() -> management.makeAdmin(admin, member.id()));

        assertEquals(
                Role.ADMIN,
                login.userForSession(session).orElseThrow().role(),
                "no new login needed");
        Map<String, String> audit = only(logs);
        assertEquals("user.make-admin", audit.get("action"));
        assertEquals(admin.toString(), audit.get("actor_id"));
        assertEquals(member.id().toString(), audit.get("target_id"));
        assertEquals("MEMBER", audit.get("detail_old_role"));
        assertEquals("ADMIN", audit.get("detail_new_role"));
    }

    @Test
    void onlyMembersCanBeMadeAdminAndOnlyByAdmins() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        UserSummary member = accounts.newUser(Role.MEMBER);
        assertThrows(AccessDenied.class, () -> management.makeAdmin(admin, otherAdmin.id()));
        assertThrows(AccessDenied.class, () -> management.makeAdmin(admin, master));
        assertThrows(AccessDenied.class, () -> management.makeAdmin(memberActor, member.id()));
        assertEquals(Role.MEMBER, users.findById(member.id()).orElseThrow().role());
        assertEquals(Role.MASTER, users.findById(master).orElseThrow().role());
    }

    @Test
    void theMasterTakesAwayAdminRightsAtOnce() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        String session =
                ((LoginResult.LoggedIn) login.login(otherAdmin.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        List<LogRecord> logs =
                TestLogs.capture(() -> management.makeMember(master, otherAdmin.id()));
        assertEquals(Role.MEMBER, login.userForSession(session).orElseThrow().role());
        Map<String, String> audit = only(logs);
        assertEquals("user.make-member", audit.get("action"));
        assertEquals("ADMIN", audit.get("detail_old_role"));
        assertEquals("MEMBER", audit.get("detail_new_role"));
    }

    @Test
    void anAdminCannotTakeAwayAdminRightsAndNobodyCanDemoteTheMaster() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                AccessDenied.class,
                                () -> management.makeMember(admin, otherAdmin.id())));
        assertEquals("denied", only(logs).get("result"));
        assertThrows(AccessDenied.class, () -> management.makeMember(admin, admin));
        assertThrows(AccessDenied.class, () -> management.makeMember(master, master)); // USR-13
        assertThrows(
                AccessDenied.class,
                () -> management.makeMember(master, accounts.newUser(Role.MEMBER).id()));
        assertEquals(Role.ADMIN, users.findById(otherAdmin.id()).orElseThrow().role());
        assertEquals(Role.MASTER, users.findById(master).orElseThrow().role());
    }

    // Who may ask at all (the server checks the role first)

    @Test
    void aMemberOrAnUnknownUserCanDoNothing() {
        UserSummary victim = accounts.newUser(Role.MEMBER);
        UUID nobody = UUID.randomUUID();
        for (UUID actor : new UUID[] {memberActor, nobody}) {
            assertThrows(AccessDenied.class, () -> management.add(actor, newAddress()));
            assertThrows(AccessDenied.class, () -> management.resendRecovery(actor, victim.id()));
            assertThrows(AccessDenied.class, () -> management.remove(actor, victim.id()));
            assertThrows(AccessDenied.class, () -> management.makeAdmin(actor, victim.id()));
            assertThrows(AccessDenied.class, () -> management.makeMember(actor, victim.id()));
            assertThrows(
                    AccessDenied.class,
                    () -> management.list(actor, new Query(Sort.EMAIL, true, null)));
            assertThrows(AccessDenied.class, () -> management.find(actor, victim.id()));
        }
        assertTrue(users.findById(victim.id()).isPresent());
        assertEquals(Role.MEMBER, users.findById(victim.id()).orElseThrow().role());
    }

    @Test
    void aRefusedMemberRequestIsAuditedWithTheActorAndTheRealAction() {
        // LGG-11, LGG-16: someone who should not be there is visible in the audit trail
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                AccessDenied.class, () -> management.remove(memberActor, admin)));
        Map<String, String> audit = only(logs);
        assertEquals("user.remove", audit.get("action"));
        assertEquals("denied", audit.get("result"));
        assertEquals(memberActor.toString(), audit.get("actor_id"));
        assertEquals("MEMBER", audit.get("actor_role"));
    }

    @Test
    void auditEntriesHaveTheFixedMarkerAndNoLineBreaks() {
        List<LogRecord> logs = TestLogs.capture(() -> management.add(admin, newAddress()));
        Map<String, String> audit = only(logs);
        assertEquals("true", audit.get("audit"));
        for (String value : audit.values()) {
            assertFalse(value.contains("\n") || value.contains("\r"));
            assertTrue(value.length() <= 200);
        }
    }

    // The overview (USR-7, USR-8)

    @Test
    void theOverviewShowsWhatTheActorMayDoWithEachUser() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);

        Map<UUID, UserRow> asAdmin = rows(admin, new Query(Sort.EMAIL, true, null));
        assertTrue(asAdmin.get(member.id()).actions().remove());
        assertTrue(asAdmin.get(member.id()).actions().makeAdmin());
        assertFalse(
                asAdmin.get(otherAdmin.id()).actions().remove(),
                "admins cannot remove admins (USR-11)");
        assertFalse(asAdmin.get(otherAdmin.id()).actions().makeMember());
        assertFalse(asAdmin.get(master).actions().remove());
        assertFalse(asAdmin.get(master).actions().resendRecovery());

        Map<UUID, UserRow> asMaster = rows(master, new Query(Sort.EMAIL, true, null));
        assertTrue(asMaster.get(otherAdmin.id()).actions().remove());
        assertTrue(asMaster.get(otherAdmin.id()).actions().makeMember());
        assertFalse(asMaster.get(master).actions().remove());
    }

    private Map<UUID, UserRow> rows(UUID actor, Query query) {
        Map<UUID, UserRow> rows = new java.util.LinkedHashMap<>();
        management.list(actor, query).forEach(row -> rows.put(row.user().id(), row));
        return rows;
    }

    @Test
    void theOverviewCanBeFilteredByRole() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        var members = rows(admin, new Query(Sort.EMAIL, true, Role.MEMBER));
        assertTrue(members.containsKey(member.id()));
        assertFalse(members.containsKey(otherAdmin.id()));
        assertTrue(members.values().stream().allMatch(r -> r.user().role() == Role.MEMBER));
        var masters = management.list(admin, new Query(Sort.EMAIL, true, Role.MASTER));
        assertEquals(1, masters.size());
        assertEquals(TestAccounts.MASTER_EMAIL, masters.get(0).user().email());
    }

    @Test
    void theOverviewCanBeSortedOnEveryColumnInBothDirections() {
        UserSummary first = accounts.newUser(Role.MEMBER);
        clock.advance(Duration.ofMinutes(1));
        UserSummary second = accounts.newUser(Role.MEMBER);
        clock.advance(Duration.ofMinutes(1));
        UserSummary third = accounts.newUser(Role.MEMBER);
        login.login(second.email(), TestAccounts.PASSWORD); // second logs in first
        clock.advance(Duration.ofMinutes(1));
        login.login(third.email(), TestAccounts.PASSWORD);
        recovery.sendTo(third.id());
        clock.advance(Duration.ofMinutes(1));
        recovery.sendTo(first.id());

        assertOrder(Sort.ADDED, first, second, third);
        assertOrder(Sort.LAST_LOGIN, first, second, third); // never logged in counts as the oldest
        assertOrder(Sort.RECOVERY_EMAIL, second, third, first);
        for (Sort sort : Sort.values()) {
            List<UUID> up =
                    management.list(admin, new Query(sort, true, null)).stream()
                            .map(r -> r.user().id())
                            .toList();
            List<UUID> down =
                    management.list(admin, new Query(sort, false, null)).stream()
                            .map(r -> r.user().id())
                            .toList();
            assertEquals(up.reversed(), down, "descending is the reverse of ascending for " + sort);
        }
        List<String> byEmail =
                management.list(admin, new Query(Sort.EMAIL, true, null)).stream()
                        .map(r -> r.user().email())
                        .toList();
        assertEquals(byEmail.stream().sorted().toList(), byEmail);
        List<Role> byRole =
                management.list(admin, new Query(Sort.ROLE, true, null)).stream()
                        .map(r -> r.user().role())
                        .toList();
        assertEquals(byRole.stream().sorted().toList(), byRole);
    }

    private void assertOrder(Sort sort, UserSummary... expected) {
        List<UUID> order =
                management.list(admin, new Query(sort, true, Role.MEMBER)).stream()
                        .map(r -> r.user().id())
                        .toList();
        int previous = -1;
        for (UserSummary user : expected) {
            int position = order.indexOf(user.id());
            assertTrue(position > previous, sort + ": " + user.email() + " is in the wrong place");
            previous = position;
        }
    }
}
