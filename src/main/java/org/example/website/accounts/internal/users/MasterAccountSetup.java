package org.example.website.accounts.internal.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import org.example.website.accounts.api.rules.AccountsRules;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.internal.config.AccountsConfig;
import org.example.website.accounts.internal.passwords.PasswordHasher;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditTrail;
import org.jboss.logging.Logger;

/**
 * Makes the master account match {@code MASTER_EMAIL} and {@code MASTER_PASSWORD} (USR-14, USR-15).
 * The environment variables are the source of truth. There is never more than one master.
 */
@ApplicationScoped
public class MasterAccountSetup {

    private static final Logger LOG = Logger.getLogger(MasterAccountSetup.class);

    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject PasswordHasher hasher;
    @Inject AccountsConfig config;
    @Inject Clock clock;
    @Inject AuditTrail audit;

    public void ensureMasterAccount() {
        var problems =
                MasterRules.problems(
                        config.masterEmail().orElse(null), config.masterPassword().orElse(null));
        if (!problems.isEmpty()) {
            // The messages never hold the values
            LOG.errorf("Master account setup: %s", String.join("; ", problems));
            throw new IllegalStateException("Invalid master account settings");
        }
        String email = AccountsRules.normalizeEmail(config.masterEmail().orElseThrow());
        String password = config.masterPassword().orElseThrow();

        // A previous master with another address becomes an admin (USR-15)
        for (UserRecord other : users.findAll()) {
            if (other.role() == Role.MASTER && !other.email().equals(email)) {
                users.update(other.withRole(Role.ADMIN));
                LOG.info("Master account setup: the previous master became an admin");
            }
        }

        var existing = users.findByEmail(email);
        if (existing.isEmpty()) {
            UserRecord created =
                    new UserRecord(
                            UUID.randomUUID(),
                            email,
                            Role.MASTER,
                            hasher.hash(password),
                            clock.instant(),
                            null,
                            null);
            users.insertIfEmailFree(created);
            LOG.info("Master account setup: created");
            audited(created, "created");
            return;
        }
        UserRecord master = existing.get();
        boolean samePassword =
                master.passwordHash() != null
                        && hasher.verify(password, master.passwordHash())
                        && !hasher.needsRehash(master.passwordHash());
        if (master.role() == Role.MASTER && samePassword) {
            LOG.info("Master account setup: already up to date");
            audited(master, "unchanged");
            return;
        }
        users.update(
                master.withRole(Role.MASTER)
                        .withPasswordHash(
                                samePassword ? master.passwordHash() : hasher.hash(password)));
        if (!samePassword) {
            sessions.deleteAllForUser(master.id()); // a new password ends the old sessions
        }
        LOG.info("Master account setup: updated");
        audited(master, "updated");
    }

    /** The result of the setup is part of the audit trail (LGG-9). */
    private void audited(UserRecord master, String outcome) {
        audit.record(
                new AuditEntry(
                        AuditAction.MASTER_SETUP,
                        new AuditEntry.Person(master.id(), master.email(), Role.MASTER.name()),
                        null,
                        AuditEntry.Result.SUCCESS,
                        java.util.Map.of("outcome", outcome)));
    }
}
