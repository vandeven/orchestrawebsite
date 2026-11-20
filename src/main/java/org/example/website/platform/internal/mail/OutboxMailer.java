package org.example.website.platform.internal.mail;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.example.website.platform.api.mail.Email;
import org.example.website.platform.api.mail.Outbox;

/**
 * Keeps sent mail in memory instead of sending it (ARC-6). Used by tests and local work. It is
 * public so that tests and the development page can read it.
 */
@ApplicationScoped
public class OutboxMailer implements Outbox {

    private final List<Email> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    void add(Email email) {
        sent.add(email);
    }

    @Override
    public List<Email> sent() {
        return List.copyOf(sent);
    }

    @Override
    public void clear() {
        sent.clear();
    }

    @Override
    public void failSends(boolean failing) {
        this.failing = failing;
    }

    boolean failing() {
        return failing;
    }
}
