package org.example.website.platform.api;

import java.util.Optional;

/**
 * The network address of the visitor who sent the request being handled, for rate limits (LOG-13).
 * It is personal data: it is only ever hashed, never logged or stored as it is (LGG-5).
 */
public interface ClientAddress {

    /**
     * The address, or nothing when it cannot be found. IPv4 addresses are returned as they are. For
     * IPv6 only the first 64 bits are kept, because one visitor controls a whole /64 network and
     * could otherwise change address at will.
     */
    Optional<String> current();
}
