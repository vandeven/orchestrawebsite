package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Chooses where accounts are stored (ARC-6): Firestore, or the in-memory stores when
 * {@code accounts.allow-in-memory-storage} is switched on (tests, development, the container
 * smoke test). A real deployment never switches it on. The rest of the component only knows the
 * repository interfaces.
 */
@ApplicationScoped
class AccountsStorage {

    @Inject AccountsConfig config;
    @Inject InMemoryStorage memory;
    @Inject FirestoreUsers firestoreUsers;
    @Inject FirestoreSessions firestoreSessions;
    @Inject FirestoreRecoveryTokens firestoreRecoveryTokens;
    @Inject FirestoreAttempts firestoreAttempts;

    @Produces
    @ApplicationScoped
    UserRepository users() {
        return config.allowInMemoryStorage() ? memory : firestoreUsers;
    }

    @Produces
    @ApplicationScoped
    SessionRepository sessions() {
        return config.allowInMemoryStorage() ? memory : firestoreSessions;
    }

    @Produces
    @ApplicationScoped
    RecoveryTokenRepository recoveryTokens() {
        return config.allowInMemoryStorage() ? memory : firestoreRecoveryTokens;
    }

    @Produces
    @ApplicationScoped
    AttemptStore attempts() {
        return config.allowInMemoryStorage() ? memory : firestoreAttempts;
    }
}
