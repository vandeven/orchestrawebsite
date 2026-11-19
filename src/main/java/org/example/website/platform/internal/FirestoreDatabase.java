package org.example.website.platform.internal;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.example.website.platform.api.Database;
import org.example.website.platform.api.ServiceUnavailable;
import org.jboss.logging.Logger;

@ApplicationScoped
class FirestoreDatabase implements Database {

    private static final Logger LOG = Logger.getLogger(FirestoreDatabase.class);

    @Inject FirestoreConfig config;

    private Firestore client;

    @Override
    public synchronized Firestore firestore() {
        if (client == null) {
            FirestoreOptions.Builder options =
                    FirestoreOptions.newBuilder().setDatabaseId(config.databaseId());
            config.projectId().filter(id -> !id.isBlank()).ifPresent(options::setProjectId);
            // With FIRESTORE_EMULATOR_HOST set, the client talks to the emulator without credentials
            client = options.build().getService();
        }
        return client;
    }

    @Override
    public <T> T await(ApiFuture<T> future) {
        try {
            return future.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            LOG.error("A database call was interrupted");
            throw new ServiceUnavailable("The database call was interrupted");
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.errorf("A database call took longer than %s", config.timeout());
            throw new ServiceUnavailable("The database call timed out");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime && !isFromGoogle(runtime)) {
                throw runtime; // a mistake in our own code inside a transaction: not hidden
            }
            LOG.errorf("A database call failed (%s)", cause.getClass().getSimpleName());
            throw new ServiceUnavailable("The database call failed");
        }
    }

    private static boolean isFromGoogle(Throwable error) {
        String name = error.getClass().getName();
        return name.startsWith("com.google.") || name.startsWith("io.grpc.");
    }

    @PreDestroy
    synchronized void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("Closing the database connection failed");
            }
            client = null;
        }
    }
}
