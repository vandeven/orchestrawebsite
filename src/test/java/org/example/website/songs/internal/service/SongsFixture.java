package org.example.website.songs.internal.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.audit.api.AuditEntry;
import org.example.website.platform.internal.limits.InMemoryRateLimiter;
import org.example.website.songs.internal.config.SongsConfig;
import org.example.website.songs.internal.files.InMemoryFileStorage;
import org.example.website.songs.internal.store.memory.InMemorySongs;

/** Test only: the songs services wired to in-memory stores, without the application running. */
final class SongsFixture {

    static final Instant START = Instant.parse("2026-11-24T10:00:00Z");

    final SongsConfig config =
            new SongsConfig() {
                @Override
                public String filesBucket() {
                    return "files";
                }

                @Override
                public int uploadsPerHour() {
                    return 200;
                }

                @Override
                public boolean allowInMemoryStorage() {
                    return true;
                }
            };
    Instant now = START;
    final Clock clock =
            new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now;
                }
            };
    final InMemorySongs store = new InMemorySongs(config);
    final InMemoryFileStorage storage = new InMemoryFileStorage();
    final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
    final List<AuditEntry> audited = new ArrayList<>();
    final SongManagementImpl management = new SongManagementImpl();
    final SongCatalogImpl catalog = new SongCatalogImpl();

    final UserSummary admin = user(Role.ADMIN);
    final UserSummary master = user(Role.MASTER);
    final UserSummary member = user(Role.MEMBER);

    SongsFixture() {
        management.songs = store;
        management.files = store;
        management.storage = storage;
        management.audit = audited::add;
        management.limiter = limiter;
        management.config = config;
        management.clock = clock;
        catalog.songs = store;
        catalog.files = store;
        catalog.storage = storage;
        catalog.clock = clock;
    }

    static UserSummary user(Role role) {
        return new UserSummary(
                UUID.randomUUID(),
                role.name().toLowerCase() + "@example.test",
                role,
                START,
                null,
                true,
                null);
    }
}
