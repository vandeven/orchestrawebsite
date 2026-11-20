package org.example.website.accounts.internal.store;

import java.time.Instant;
import java.util.UUID;

/** A session as it is stored: only a hash of the token is kept (LOG-11). */
public record SessionRecord(String tokenHash, UUID userId, Instant expiresAt) {}
