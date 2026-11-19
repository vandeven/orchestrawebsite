package org.example.website.audit.internal;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditTrail;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

/**
 * Writes audit entries as structured log lines (LGG-13): the fields are in the logging context, so
 * the JSON log has them as separate fields, with {@code audit=true} to filter on. The time and the
 * request ID are added by the logging itself (LGG-2, LGG-3). Values that come from visitors are
 * cleaned of line breaks and cut short (LGG-18).
 */
@ApplicationScoped
class LogAuditTrail implements AuditTrail {

    private static final Logger LOG = Logger.getLogger("audit");
    private static final int MAX = 200;

    @Override
    public void record(AuditEntry entry) {
        List<String> keys = new ArrayList<>();
        put(keys, "audit", "true");
        put(keys, "action", entry.action().code());
        put(keys, "result", entry.result().code());
        put(keys, "actor_id", String.valueOf(entry.actor().id()));
        put(keys, "actor_email", entry.actor().email());
        put(keys, "actor_role", entry.actor().role());
        if (entry.target() != null) {
            put(keys, "target_id", String.valueOf(entry.target().id()));
            put(keys, "target_email", entry.target().email());
            put(keys, "target_role", entry.target().role());
        }
        for (Map.Entry<String, String> detail : entry.details().entrySet()) {
            put(keys, "detail_" + detail.getKey(), detail.getValue());
        }
        try {
            LOG.infof("audit %s %s", entry.action().code(), entry.result().code());
        } finally {
            keys.forEach(MDC::remove);
        }
    }

    private static void put(List<String> keys, String key, String value) {
        if (value == null) {
            return;
        }
        MDC.put(key, clean(value));
        keys.add(key);
    }

    /** No line breaks or control characters, at most 200 characters (LGG-18). */
    static String clean(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        String text = out.toString();
        return text.length() > MAX ? text.substring(0, MAX) : text;
    }
}
