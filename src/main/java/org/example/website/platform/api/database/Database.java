package org.example.website.platform.api.database;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import org.example.website.platform.api.errors.ServiceUnavailable;

/**
 * The one connection to Firestore (ARC-5, ARC-14). It is created when it is first needed, not at
 * startup. Components keep their own collections and use this only through their repositories.
 */
public interface Database {

    /** The Firestore client. Created on the first call. */
    Firestore firestore();

    /**
     * Waits for the result of a database call, for at most the configured time. Any failure or
     * delay becomes {@link ServiceUnavailable} (the 503 page, ARC-8). The log gets the kind of
     * failure and never the data that was being written.
     */
    <T> T await(ApiFuture<T> future);
}
