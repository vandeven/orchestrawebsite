package org.example.website.platform.internal.database;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.example.website.platform.api.database.Database;

/**
 * The application is only ready when it can read from Firestore (HOS-6). The check reads one
 * document that does not exist, which proves the connection and the permission without needing any
 * data. Failing it keeps a new version from getting traffic. It is a readiness check only: a
 * database outage must not make Cloud Run restart the container (liveness).
 */
@Readiness
@ApplicationScoped
class DatabaseReadiness implements HealthCheck {

    @Inject Database database;
    @Inject FirestoreConfig config;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("database");
        if (!config.readinessCheck()) {
            return response.up().withData("checked", false).build();
        }
        try {
            // The failure is logged by Database.await, without any data
            database.await(database.firestore().collection("health").document("probe").get());
            return response.up().withData("checked", true).build();
        } catch (RuntimeException e) {
            return response.down().build();
        }
    }
}
