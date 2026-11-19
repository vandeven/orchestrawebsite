package org.example.website.platform.internal;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.example.website.platform.api.ClientAddress;
import org.example.website.platform.api.PlatformConfig;

/**
 * Finds the address of the visitor. Behind Cloud Run the address is in {@code X-Forwarded-For}, but
 * a visitor can send that header too and the proxy only adds its own entry at the end. So only the
 * entry that the configured number of trusted proxies added is believed (the n-th from the end),
 * never the first one, which the visitor controls. Without trusted proxies the address of the
 * connection is used and the header is ignored.
 */
@ApplicationScoped
class RequestClientAddress implements ClientAddress {

    /** Only characters that can be in an IP address, so no name lookup is ever done. */
    private static final Pattern LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    @Inject CurrentVertxRequest currentRequest;
    @Inject PlatformConfig config;

    @Override
    public Optional<String> current() {
        RoutingContext context = currentRequest.getCurrent();
        if (context == null) {
            return Optional.empty();
        }
        String remote =
                context.request().remoteAddress() == null
                        ? null
                        : context.request().remoteAddress().hostAddress();
        return resolve(
                remote, context.request().getHeader("X-Forwarded-For"), config.clientIpProxyHops());
    }

    /**
     * The visitor's address from the connection and the forwarded header (see the class comment).
     */
    static Optional<String> resolve(String remote, String forwardedFor, int trustedProxies) {
        String chosen = remote;
        if (trustedProxies > 0 && forwardedFor != null) {
            String[] entries = forwardedFor.split(",");
            if (entries.length >= trustedProxies) {
                chosen = entries[entries.length - trustedProxies].strip();
            }
        }
        return normalize(chosen);
    }

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    /**
     * A valid literal address, with IPv6 cut to its /64 network. Anything else gives nothing. IPv4
     * must be four numbers from 0 to 255: Java would also accept short forms such as "1.2.3", which
     * a visitor could use to make up addresses.
     */
    static Optional<String> normalize(String value) {
        if (value == null || !LITERAL.matcher(value).matches()) {
            return Optional.empty();
        }
        if (value.indexOf(':') < 0) {
            java.util.regex.Matcher matcher = IPV4.matcher(value);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(matcher.group(i + 1));
                if (octets[i] > 255) {
                    return Optional.empty();
                }
            }
            return Optional.of(octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3]);
        }
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress(); // a literal: no DNS lookup
            if (bytes.length == 4) {
                return Optional
                        .empty(); // an IPv4-mapped form: not accepted, the proxy sends plain IPv4
            }
            return Optional.of("v6:" + HexFormat.of().formatHex(bytes, 0, 8));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
