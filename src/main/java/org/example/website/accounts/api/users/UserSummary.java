package org.example.website.accounts.api.users;

import java.time.Instant;
import java.util.UUID;

/** What other components may know about a user. Never contains the password hash. */
public record UserSummary(
        UUID id,
        String email,
        Role role,
        Instant createdAt,
        Instant lastLoginAt,
        boolean hasPassword,
        Instant recoveryEmailSentAt) {}
