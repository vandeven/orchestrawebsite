package org.example.website.platform.internal.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.example.website.platform.api.database.Database;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * HOS-6: a version that cannot reach Firestore is not ready, so it never gets traffic. The first
 * tests need no database; the last one reads from the Firestore emulator.
 */
@QuarkusTest
class DatabaseReadinessTest {

    private static final class Settings implements FirestoreConfig {
        private final boolean check;

        Settings(boolean check) {
            this.check = check;
        }

        @Override
        public Optional<String> projectId() {
            return Optional.empty();
        }

        @Override
        public String databaseId() {
            return "(default)";
        }

        @Override
        public boolean readinessCheck() {
            return check;
        }

        @Override
        public Duration timeout() {
            return Duration.ofSeconds(1);
        }
    }

    @Inject Database real;

    private static HealthCheckResponse run(boolean check, Database database) {
        DatabaseReadiness readiness = new DatabaseReadiness();
        readiness.config = new Settings(check);
        readiness.database = database;
        return readiness.call();
    }

    /** A database that fails as soon as it is used, like one that cannot be reached. */
    private static Database broken() {
        return new Database() {
            @Override
            public Firestore firestore() {
                throw new ServiceUnavailable("no connection");
            }

            @Override
            public <T> T await(ApiFuture<T> future) {
                throw new ServiceUnavailable("no connection");
            }
        };
    }

    @Test
    void whenTheCheckIsOffTheApplicationIsReadyWithoutTouchingTheDatabase() {
        HealthCheckResponse response = run(false, broken());
        assertEquals(Status.UP, response.getStatus());
        assertEquals("database", response.getName());
    }

    @Test
    void whenTheDatabaseCannotBeReachedTheApplicationIsNotReady() {
        assertEquals(Status.DOWN, run(true, broken()).getStatus());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
    void aWorkingDatabaseIsReady() {
        assertEquals(Status.UP, run(true, real).getStatus());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
    void aReadThatFailsMeansNotReady() {
        Database failing =
                new Database() {
                    @Override
                    public Firestore firestore() {
                        return real.firestore();
                    }

                    @Override
                    public <T> T await(ApiFuture<T> future) {
                        throw new ServiceUnavailable("read failed");
                    }
                };
        assertEquals(Status.DOWN, run(true, failing).getStatus());
    }
}
