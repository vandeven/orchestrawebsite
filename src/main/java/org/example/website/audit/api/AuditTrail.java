package org.example.website.audit.api;

/**
 * Writes the audit trail (LGG-9 to LGG-13). Services call it, never the web layer, so an action is
 * audited whichever way it was started (ARC-12). It cannot be switched off.
 */
public interface AuditTrail {

    /** Writes the entry to the log output, marked with {@code audit=true}. */
    void record(AuditEntry entry);
}
