package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.Role;
import org.jboss.logging.Logger;

/**
 * Makes the master account match {@code MASTER_EMAIL} and {@code MASTER_PASSWORD} (USR-14, USR-15).
 * The environment variables are the source of truth. There is never more than one master.
 */
@ApplicationScoped
class MasterAccountSetup {

    private static final Logger LOG = Logger.getLogger(MasterAccountSetup.class);

    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject PasswordHasher hasher;
    @Inject AccountsConfig config;
    @Inject Clock clock;

    void ensureMasterAccount() {
        String email = AccountsRules.normalizeEmail(config.masterEmail());
        String password = config.masterPassword();

        // A previous master with another address becomes an admin (USR-15)
        for (UserRecord other : users.findAll()) {
            if (other.role() == Role.MASTER && !other.email().equals(email)) {
                users.update(other.withRole(Role.ADMIN));
                LOG.info("Master account setup: the previous master became an admin");
            }
        }

        var existing = users.findByEmail(email);
        if (existing.isEmpty()) {
            users.insertIfEmailFree(
                    new UserRecord(
                            UUID.randomUUID(),
                            email,
                            Role.MASTER,
                            hasher.hash(password),
                            clock.instant(),
                            null,
                            null));
            LOG.info("Master account setup: created");
            return;
        }
        UserRecord master = existing.get();
        boolean samePassword =
                master.passwordHash() != null
                        && hasher.verify(password, master.passwordHash())
                        && !hasher.needsRehash(master.passwordHash());
        if (master.role() == Role.MASTER && samePassword) {
            LOG.info("Master account setup: already up to date");
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
    }
}
