package org.example.website.songs.api;

import java.time.Instant;

/** A short-lived signed address to one file in storage (HOS-13). */
public record FileLink(String url, Instant expiresAt) {}
