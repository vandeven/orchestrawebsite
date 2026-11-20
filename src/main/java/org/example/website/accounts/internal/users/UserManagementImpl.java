package org.example.website.accounts.internal.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.example.website.accounts.api.RecoveryService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserManagement;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditEntry.Person;
import org.example.website.audit.api.AuditEntry.Result;
import org.example.website.audit.api.AuditTrail;
import org.example.website.platform.api.AccessDenied;
import org.example.website.platform.api.ApplicationError;
import org.example.website.platform.api.ResourceNotFound;
import org.example.website.platform.api.ServiceUnavailable;

@ApplicationScoped
class UserManagementImpl implements UserManagement {

    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject RecoveryTokenRepository recoveryTokens;
    @Inject UserService userService;
    @Inject RecoveryService recovery;
    @Inject AuditTrail audit;

    @Override
    public List<UserRow> list(UUID actorId, Query query) {
        UserRecord actor = manager(actorId, null);
        Comparator<UserRecord> order = comparator(query.sort());
        return users.findAll().stream()
                .filter(user -> query.role() == null || user.role() == query.role())
                .sorted(query.ascending() ? order : order.reversed())
                .map(user ->
                        new UserRow(user.summary(), UserRules.actions(actor.role(), user.role())))
                .toList();
    }

    /**
     * Never logged in and never sent count as the oldest moment, so they come first when ascending.
     */
    private static Comparator<UserRecord> comparator(Sort sort) {
        Comparator<UserRecord> byEmail = Comparator.comparing(UserRecord::email);
        Comparator<UserRecord> order =
                switch (sort) {
                    case EMAIL -> byEmail;
                    case ROLE -> Comparator.comparing(UserRecord::role).thenComparing(byEmail);
                    case ADDED ->
                            Comparator.comparing(UserRecord::createdAt).thenComparing(byEmail);
                    case LAST_LOGIN ->
                            Comparator.comparing(
                                            UserRecord::lastLoginAt,
                                            Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(byEmail);
                    case RECOVERY_EMAIL ->
                            Comparator.comparing(
                                            UserRecord::recoveryEmailSentAt,
                                            Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(byEmail);
                };
        return order;
    }

    @Override
    public UserRow find(UUID actorId, UUID targetId) {
        UserRecord actor = manager(actorId, null);
        UserRecord target =
                users.findById(targetId).orElseThrow(() -> new ResourceNotFound("No such user"));
        return new UserRow(target.summary(), UserRules.actions(actor.role(), target.role()));
    }

    @Override
    public AddResult add(UUID actorId, String email) {
        UserRecord actor = manager(actorId, AuditAction.USER_ADD);
        Person actorPerson = person(actor);
        var summary =
                attempt(
                        AuditAction.USER_ADD,
                        actorPerson,
                        null,
                        Map.of(),
                        () -> userService.createUser(email, Role.MEMBER));
        Person target = new Person(summary.id(), summary.email(), summary.role().name());
        boolean sent = true;
        try {
            recovery.sendTo(summary.id()); // USR-5; a failure is logged and alerted there (USR-24)
        } catch (ServiceUnavailable e) {
            sent = false;
        }
        audit.record(
                new AuditEntry(
                        AuditAction.USER_ADD,
                        actorPerson,
                        target,
                        Result.SUCCESS,
                        Map.of("recovery_email", sent ? "sent" : "failed")));
        return new AddResult(summary, sent);
    }

    @Override
    public void resendRecovery(UUID actorId, UUID targetId) {
        UserRecord actor = manager(actorId, AuditAction.USER_RESEND_RECOVERY);
        UserRecord target =
                target(actor, AuditAction.USER_RESEND_RECOVERY, targetId, Actions::resendRecovery);
        attempt(
                AuditAction.USER_RESEND_RECOVERY,
                person(actor),
                person(target),
                Map.of(),
                () -> {
                    recovery.sendTo(target.id());
                    return null;
                });
    }

    @Override
    public void remove(UUID actorId, UUID targetId) {
        UserRecord actor = manager(actorId, AuditAction.USER_REMOVE);
        UserRecord target = target(actor, AuditAction.USER_REMOVE, targetId, Actions::remove);
        attempt(
                AuditAction.USER_REMOVE,
                person(actor),
                person(target),
                Map.of("role", target.role().name()),
                () -> {
                    users.delete(target.id());
                    sessions.deleteAllForUser(target.id()); // the user loses access at once (USR-9)
                    recoveryTokens.deleteForUser(target.id());
                    return null;
                });
    }

    @Override
    public void makeAdmin(UUID actorId, UUID targetId) {
        changeRole(actorId, targetId, AuditAction.USER_MAKE_ADMIN, Role.ADMIN, Actions::makeAdmin);
    }

    @Override
    public void makeMember(UUID actorId, UUID targetId) {
        changeRole(
                actorId, targetId, AuditAction.USER_MAKE_MEMBER, Role.MEMBER, Actions::makeMember);
    }

    private void changeRole(
            UUID actorId,
            UUID targetId,
            AuditAction action,
            Role newRole,
            java.util.function.Predicate<Actions> allowed) {
        UserRecord actor = manager(actorId, action);
        UserRecord target = target(actor, action, targetId, allowed);
        attempt(
                action,
                person(actor),
                person(target),
                Map.of("old_role", target.role().name(), "new_role", newRole.name()),
                () -> {
                    users.update(
                            target.withRole(
                                    newRole)); // read on every request, so it counts at once
                    // (USR-10)
                    return null;
                });
    }

    // Checks and audit

    /**
     * The acting user, who must be an admin or the master. Anyone else is refused and audited
     * (LGG-11).
     */
    private UserRecord manager(UUID actorId, AuditAction action) {
        UserRecord actor = users.findById(actorId).orElse(null);
        if (actor == null || actor.role() == Role.MEMBER) {
            if (action != null) { // reading the overview is not an audited change
                Person who = actor == null ? new Person(actorId, null, null) : person(actor);
                audit.record(
                        new AuditEntry(
                                action,
                                who,
                                null,
                                Result.DENIED,
                                Map.of("reason", "not an admin")));
            }
            throw new AccessDenied("The acting user is not an admin");
        }
        return actor;
    }

    /** The target, when the actor may do this action with it. Otherwise refused and audited. */
    private UserRecord target(
            UserRecord actor,
            AuditAction action,
            UUID targetId,
            java.util.function.Predicate<Actions> allowed) {
        UserRecord target =
                users.findById(targetId).orElseThrow(() -> new ResourceNotFound("No such user"));
        if (!allowed.test(UserRules.actions(actor.role(), target.role()))) {
            audit.record(
                    new AuditEntry(
                            action,
                            person(actor),
                            person(target),
                            Result.DENIED,
                            Map.of(
                                    "reason",
                                    actor.role() + " may not do this to " + target.role())));
            throw new AccessDenied("Not allowed for this role combination");
        }
        return target;
    }

    /** Runs the action and writes one audit entry with the result (LGG-9, LGG-11). */
    private <T> T attempt(
            AuditAction action,
            Person actor,
            Person target,
            Map<String, String> details,
            Supplier<T> work) {
        try {
            T result = work.get();
            if (action
                    != AuditAction.USER_ADD) { // an add is audited by the caller, with the new user
                audit.record(new AuditEntry(action, actor, target, Result.SUCCESS, details));
            }
            return result;
        } catch (ApplicationError e) {
            audit.record(
                    new AuditEntry(
                            action,
                            actor,
                            target,
                            e.status() >= 500 ? Result.FAILED : Result.DENIED,
                            Map.of("error", e.getClass().getSimpleName())));
            throw e;
        } catch (RuntimeException e) {
            audit.record(
                    new AuditEntry(
                            action,
                            actor,
                            target,
                            Result.FAILED,
                            Map.of("error", e.getClass().getSimpleName())));
            throw e;
        }
    }

    private static Person person(UserRecord user) {
        return new Person(user.id(), user.email(), user.role().name());
    }
}
