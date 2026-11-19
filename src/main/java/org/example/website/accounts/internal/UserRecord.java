package org.example.website.accounts.internal;

import java.time.Instant;
import java.util.UUID;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserSummary;

/** A user as it is stored. The email address is always normalized. */
record UserRecord(
        UUID id,
        String email,
        Role role,
        String passwordHash,
        Instant createdAt,
        Instant lastLoginAt,
        Instant recoveryEmailSentAt) {

    UserRecord withPasswordHash(String hash) {
        return new UserRecord(id, email, role, hash, createdAt, lastLoginAt, recoveryEmailSentAt);
    }

    UserRecord withRole(Role newRole) {
        return new UserRecord(
                id, email, newRole, passwordHash, createdAt, lastLoginAt, recoveryEmailSentAt);
    }

    UserRecord withLastLogin(Instant moment) {
        return new UserRecord(
                id, email, role, passwordHash, createdAt, moment, recoveryEmailSentAt);
    }

    UserRecord withRecoveryEmailSentAt(Instant moment) {
        return new UserRecord(id, email, role, passwordHash, createdAt, lastLoginAt, moment);
    }

    UserSummary summary() {
        return new UserSummary(
                id, email, role, createdAt, lastLoginAt, passwordHash != null, recoveryEmailSentAt);
    }
}
