package org.example.website.accounts.internal.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserManagement.Actions;
import org.junit.jupiter.api.Test;

/** USR-6, USR-9 to USR-13: every acting role against every target role, for every action. */
class UserRulesTest {

    private record Pair(Role actor, Role target) {}

    private static final Actions NOTHING = new Actions(false, false, false, false);

    /**
     * resend, remove, make admin, make member. Written out in full, so a change shows here first.
     */
    private static final Map<Pair, Actions> EXPECTED =
            Map.ofEntries(
                    // A member can do nothing at all
                    Map.entry(new Pair(Role.MEMBER, Role.MEMBER), NOTHING),
                    Map.entry(new Pair(Role.MEMBER, Role.ADMIN), NOTHING),
                    Map.entry(new Pair(Role.MEMBER, Role.MASTER), NOTHING),
                    // An admin: members can be sent mail, removed and made admin; admins can only
                    // be sent mail
                    Map.entry(
                            new Pair(Role.ADMIN, Role.MEMBER),
                            new Actions(true, true, true, false)),
                    Map.entry(
                            new Pair(Role.ADMIN, Role.ADMIN),
                            new Actions(true, false, false, false)),
                    Map.entry(new Pair(Role.ADMIN, Role.MASTER), NOTHING),
                    // The master: everything except touching the master account
                    Map.entry(
                            new Pair(Role.MASTER, Role.MEMBER),
                            new Actions(true, true, true, false)),
                    Map.entry(
                            new Pair(Role.MASTER, Role.ADMIN),
                            new Actions(true, true, false, true)),
                    Map.entry(new Pair(Role.MASTER, Role.MASTER), NOTHING));

    @Test
    void everyCombinationFollowsTheTable() {
        for (Role actor : Role.values()) {
            for (Role target : Role.values()) {
                assertEquals(
                        EXPECTED.get(new Pair(actor, target)),
                        UserRules.actions(actor, target),
                        actor + " acting on " + target);
            }
        }
        assertEquals(9, EXPECTED.size(), "all nine combinations are in the table");
    }

    @Test
    void nobodyCanTouchTheMasterAccount() {
        // USR-13, USR-16
        for (Role actor : Role.values()) {
            assertEquals(NOTHING, UserRules.actions(actor, Role.MASTER));
        }
    }

    @Test
    void onlyTheMasterCanRemoveAnAdminOrTakeAwayAdminRights() {
        // USR-11, USR-12
        assertEquals(false, UserRules.actions(Role.ADMIN, Role.ADMIN).remove());
        assertEquals(false, UserRules.actions(Role.ADMIN, Role.ADMIN).makeMember());
        assertEquals(true, UserRules.actions(Role.MASTER, Role.ADMIN).remove());
        assertEquals(true, UserRules.actions(Role.MASTER, Role.ADMIN).makeMember());
    }
}
