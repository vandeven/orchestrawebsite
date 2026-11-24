package org.example.website.site.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.example.website.platform.api.LegalConfig;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.VisitorAccess;

/**
 * What every page template needs: the name of the orchestra and the main menu for the current
 * visitor (plan: Layout and navigation). Used in templates as {@code {cdi:site.name}} and {@code
 * {cdi:site.menu(currentPath)}}. Hiding an item is not the security: every page checks the access
 * level again on the server.
 */
@Named("site")
@ApplicationScoped
public class SiteModel {

    /** One entry of the menu. A {@code post} entry is a button in a form, such as log out. */
    public record MenuItem(String label, String href, boolean current, boolean post) {}

    /**
     * The menu in the order of the plan. {@code visitorOnly} is shown to anonymous visitors only.
     */
    private record Entry(
            String label, String href, AccessLevel level, boolean visitorOnly, boolean post) {}

    private static final List<Entry> ENTRIES =
            List.of(
                    new Entry("About", "/", AccessLevel.PUBLIC, false, false),
                    new Entry(
                            "Become a member",
                            "/become-a-member",
                            AccessLevel.PUBLIC,
                            false,
                            false),
                    new Entry("Agenda", "/agenda", AccessLevel.PUBLIC, false, false),
                    new Entry("Log in", "/login", AccessLevel.PUBLIC, true, false),
                    new Entry("Member page", "/members", AccessLevel.MEMBER, false, false),
                    new Entry("Song management", "/songs/manage", AccessLevel.ADMIN, false, false),
                    new Entry("Edit pages", "/edit", AccessLevel.ADMIN, false, false),
                    new Entry("User management", "/users", AccessLevel.ADMIN, false, false),
                    new Entry("My account", "/account", AccessLevel.MEMBER, false, false),
                    new Entry("Log out", "/logout", AccessLevel.MEMBER, false, true));

    @Inject PlatformConfig config;

    @Inject LegalConfig legal;

    @Inject VisitorAccess visitor;

    public String name() {
        return config.name();
    }

    /** The address of the website without a closing slash, for canonical links (ABO-8). */
    public String baseUrl() {
        String base = config.publicBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** The foundation's details for the footer (LEG-8). */
    public record Foundation(String name, String address, String registration, String email) {}

    public Foundation foundation() {
        return new Foundation(
                legal.foundationName(),
                legal.address(),
                legal.registrationNumber(),
                legal.contactEmail());
    }

    /**
     * The canonical address of a public page, or nothing for pages behind a login and for the pages
     * search engines should not list (plan: Search engines and sharing).
     */
    public String canonical(String currentPath) {
        return currentPath != null && SeoResource.PUBLIC_PAGES.contains(currentPath)
                ? baseUrl() + currentPath
                : null;
    }

    /**
     * The address of a style sheet, script or icon of this website with the version of the build,
     * so a browser fetches the new file after each deployment and never keeps an old one.
     */
    public String asset(String path) {
        return path + "?v=" + URLEncoder.encode(config.version(), StandardCharsets.UTF_8);
    }

    /** The default picture for shared links (Open Graph). */
    public String shareImage() {
        return baseUrl() + "/images/social.png";
    }

    public List<MenuItem> menu(String currentPath) {
        AccessLevel level = visitor.currentLevel();
        boolean anonymous = level == AccessLevel.PUBLIC;
        List<MenuItem> items = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.visitorOnly() && !anonymous) {
                continue;
            }
            if (!level.allows(entry.level())) {
                continue;
            }
            items.add(
                    new MenuItem(
                            entry.label(),
                            entry.href(),
                            entry.href().equals(currentPath),
                            entry.post()));
        }
        return items;
    }
}
