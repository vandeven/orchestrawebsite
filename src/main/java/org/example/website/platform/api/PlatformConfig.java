package org.example.website.platform.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the platform component (ARC-11). Checked when the application starts. */
@ConfigMapping(prefix = "website")
public interface PlatformConfig {

    /** The name of the orchestra, 1 to 100 characters (DES-1). */
    String name();

    /** The fixed address of the website, used for every link in an email (HRD-1). */
    String publicBaseUrl();

    /** DEBUG, INFO, WARNING or ERROR, not case sensitive (LGG-6). */
    @WithDefault("INFO")
    String logLevel();

    /**
     * The address of the public images bucket, with a closing slash. Pages may show images from
     * there and the editor may upload there, nothing else leaves the website (HRD-6, HOS-22).
     */
    String imageOrigin();

    /** The version of this build: the short commit ID and the build time (HOS-35). */
    @WithDefault("dev")
    String version();

    /**
     * How many proxies in front of the application add an entry to {@code X-Forwarded-For} and are
     * trusted: 0 uses the address of the connection, 1 is Cloud Run. Never more than the real
     * number of proxies, or a visitor could choose their own address.
     */
    @WithDefault("0")
    int clientIpProxyHops();
}
