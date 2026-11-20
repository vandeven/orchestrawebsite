package org.example.website.accounts.internal.store;

import java.time.Instant;
import java.util.UUID;

/** A recovery link as it is stored: only a hash of the token is kept (USR-19). */
public record RecoveryTokenRecord(String tokenHash, UUID userId, Instant expiresAt) {}
