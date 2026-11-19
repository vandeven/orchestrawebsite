package org.example.website.site.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.VisitorAccess;

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

    @Inject VisitorAccess visitor;

    public String name() {
        return config.name();
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
