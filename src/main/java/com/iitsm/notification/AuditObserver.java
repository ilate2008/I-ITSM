package com.iitsm.notification;

import com.iitsm.repository.Repository;

/** Writes every event to the audit trail. */
public class AuditObserver implements EventObserver {
    private final Repository repo;
    public AuditObserver(Repository repo) { this.repo = repo; }
    @Override public void update(Event e) {
        repo.addAudit(new AuditRecord(e.timestamp(), e.actor(), e.type(), e.incidentId(),
                e.oldStatus(), e.newStatus(), e.description()));
    }
}
