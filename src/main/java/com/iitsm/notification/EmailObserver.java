package com.iitsm.notification;

import com.iitsm.repository.Repository;

/** Observer that sends a real e-mail for important events (P1/P2 incidents, SLA violations, escalations) through any {@link EmailSender}. */
public class EmailObserver implements EventObserver {
    private final Repository repo;
    private final EmailSender sender;

    public EmailObserver(Repository repo, EmailSender sender) { this.repo = repo; this.sender = sender; }

    static boolean isImportant(Event e) {
        return "P1".equals(e.priority()) || "P2".equals(e.priority())
                || e.type().equals("SLA_VIOLATED") || e.type().equals("INCIDENT_ESCALATED");
    }

    @Override public void update(Event e) {
        if (!isImportant(e)) return;
        String subject = "[" + e.incidentId() + "] " + e.type();
        for (String userId : e.recipients()) {
            repo.findUser(userId).ifPresent(u -> sender.send(u.getEmail(), subject, e.description()));
        }
    }
}
