package org.example.website.accounts.internal;

import java.time.Instant;
import java.util.UUID;

/** A session as it is stored: only a hash of the token is kept (LOG-11). */
record SessionRecord(String tokenHash, UUID userId, Instant expiresAt) {}
