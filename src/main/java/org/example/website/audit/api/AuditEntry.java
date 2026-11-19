package org.example.website.audit.api;

import java.util.Map;
import java.util.UUID;

/**
 * One entry of the audit trail (LGG-10): who did what to whom, and what came of it. The actor and
 * the target are both recorded with ID, email address and role, so a rights change always shows
 * both people (LGG-11). The content of texts is never part of an entry.
 */
public record AuditEntry(
        AuditAction action,
        Person actor,
        Person target,
        Result result,
        Map<String, String> details) {

    /**
     * A user, as the audit trail names them. {@code role} may be null for a target that is not a
     * user.
     */
    public record Person(UUID id, String email, String role) {}

    /** {@code success}, {@code denied} (not allowed) or {@code failed} (an error), see LGG-10. */
    public enum Result {
        SUCCESS("success"),
        DENIED("denied"),
        FAILED("failed");

        private final String code;

        Result(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public AuditEntry {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
