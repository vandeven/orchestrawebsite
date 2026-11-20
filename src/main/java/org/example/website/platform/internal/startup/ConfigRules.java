package org.example.website.platform.internal.startup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The validation rules for the platform configuration. Messages never contain the values. */
final class ConfigRules {

    static final int NAME_MAX = 100;
    static final int BASE_URL_MAX = 200;
    static final Set<String> LOG_LEVELS = Set.of("DEBUG", "INFO", "WARNING", "ERROR");

    /** The log level to use, and whether it differs from what was asked for. */
    record LogLevel(String level, boolean downgraded) {}

    private ConfigRules() {}

    /**
     * Checks the configuration and returns the problems found, an empty list when it is valid.
     *
     * @param localAddressAllowed true in the test and dev profiles, where http://localhost is fine
     */
    static List<String> problems(
            String name,
            String publicBaseUrl,
            String logLevel,
            boolean localAddressAllowed,
            int clientIpProxyHops) {
        List<String> problems = new ArrayList<>();
        if (name == null || name.isBlank() || name.length() > NAME_MAX) {
            problems.add("website.name must be 1 to " + NAME_MAX + " characters");
        }
        if (!validBaseUrl(publicBaseUrl, localAddressAllowed)) {
            problems.add(
                    "PUBLIC_BASE_URL must be an https:// address with only a host, at most "
                            + BASE_URL_MAX
                            + " characters");
        }
        if (logLevel == null || !LOG_LEVELS.contains(logLevel.trim().toUpperCase(Locale.ROOT))) {
            problems.add("LOG_LEVEL must be one of DEBUG, INFO, WARNING or ERROR");
        }
        if (clientIpProxyHops < 0 || clientIpProxyHops > 2) {
            problems.add("CLIENT_IP_PROXY_HOPS must be 0, 1 or 2");
        }
        return problems;
    }

    static boolean validBaseUrl(String value, boolean localAddressAllowed) {
        if (value == null || value.isEmpty() || value.length() > BASE_URL_MAX) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isEmpty()) {
            return false;
        }
        boolean local = scheme.equals("http") && host.equals("localhost");
        if (!scheme.equals("https") && !(local && localAddressAllowed)) {
            return false;
        }
        return uri.getUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
    }

    /** DEBUG is not accepted in production: the level becomes INFO (LGG-6). */
    static LogLevel resolveLogLevel(String requested, boolean production) {
        String level = requested.trim().toUpperCase(Locale.ROOT);
        if (production && level.equals("DEBUG")) {
            return new LogLevel("INFO", true);
        }
        return new LogLevel(level, false);
    }
}
