package org.example.website.audit.api;

/**
 * The fixed list of audited actions (LGG-9, LGG-10). An action that is not here cannot be written.
 */
public enum AuditAction {
    USER_ADD("user.add"),
    USER_RESEND_RECOVERY("user.resend-recovery"),
    USER_REMOVE("user.remove"),
    USER_MAKE_ADMIN("user.make-admin"),
    USER_MAKE_MEMBER("user.make-member"),
    AUTH_LOGIN("auth.login"),
    AUTH_LOGOUT("auth.logout"),
    MASTER_SETUP("master.setup");

    private final String code;

    AuditAction(String code) {
        this.code = code;
    }

    /** The name in the log, for example {@code user.remove}. */
    public String code() {
        return code;
    }
}
