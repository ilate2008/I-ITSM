package com.iitsm.model;

import com.iitsm.exception.InvalidStateTransitionException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Core domain entity. The status can only change through {@link #transitionTo}, which delegates to the State objects. */
public class Incident implements Serializable {
    private final String id;
    private final String reporterId;
    private final String title;
    private final String description;
    private final String service;
    private final int impact;
    private final int urgency;
    private final int affectedUsers;
    private final LocalDateTime createdAt;

    private Category category;
    private Status status = Status.NEW;
    private Priority priority;
    private String technicianId;
    private LocalDateTime responseDue, resolveDue, acknowledgedAt, resolvedAt, closedAt;
    private String resolution;
    private Feedback feedback;
    private int escalationLevel;
    private final List<Note> notes = new ArrayList<>();
    private final Set<SlaFlag> slaFlags = EnumSet.noneOf(SlaFlag.class);
    private final List<RelatedIncident> related = new ArrayList<>();

    public Incident(String id, String reporterId, String title, String description, String service,
                    int impact, int urgency, int affectedUsers, LocalDateTime createdAt) {
        this.id = id; this.reporterId = reporterId; this.title = title; this.description = description;
        this.service = service; this.impact = impact; this.urgency = urgency;
        this.affectedUsers = affectedUsers; this.createdAt = createdAt;
    }

    // ---- lifecycle (State pattern)
    public void transitionTo(Status target, LocalDateTime now) {
        if (!IncidentStates.of(status).canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status + " -> " + target + " is not allowed");
        }
        status = target;
        IncidentStates.of(target).onEnter(this, now);
    }
    public boolean canTransitionTo(Status target) { return IncidentStates.of(status).canTransitionTo(target); }
    /** For persistence adapters only: rebuilds a stored incident without replaying (and re-validating) its lifecycle. */
    public void restoreLifecycle(Status status, LocalDateTime acknowledgedAt, LocalDateTime resolvedAt, LocalDateTime closedAt) {
        this.status = status; this.acknowledgedAt = acknowledgedAt; this.resolvedAt = resolvedAt; this.closedAt = closedAt;
    }
    void markAcknowledged(LocalDateTime t) { acknowledgedAt = t; }
    void markResolved(LocalDateTime t) { resolvedAt = t; }
    void markClosed(LocalDateTime t) { closedAt = t; }

    // ---- mutators used by the service layer
    public void setCategory(Category c) { category = c; }
    public void setPriority(Priority p, LocalDateTime responseDue, LocalDateTime resolveDue) {
        this.priority = p; this.responseDue = responseDue; this.resolveDue = resolveDue;
    }
    public void assignTo(String technicianId) { this.technicianId = technicianId; }
    public void addNote(Note n) { notes.add(n); }
    public void setResolution(String r) { resolution = r; }
    public void setFeedback(Feedback f) { feedback = f; }
    public void addSlaFlag(SlaFlag f) { slaFlags.add(f); }
    public void setEscalationLevel(int l) { escalationLevel = l; }
    public void setRelated(List<RelatedIncident> r) { related.clear(); related.addAll(r); }

    // ---- queries
    public boolean hasFlag(SlaFlag f) { return slaFlags.contains(f); }
    public String getSlaStatus() {
        if (slaFlags.contains(SlaFlag.RESPONSE_VIOLATED) || slaFlags.contains(SlaFlag.RESOLUTION_VIOLATED)) return "BREACHED";
        if (slaFlags.contains(SlaFlag.RESPONSE_WARNING) || slaFlags.contains(SlaFlag.RESOLUTION_WARNING)) return "AT_RISK";
        return "ON_TRACK";
    }
    public String getId() { return id; }
    public String getReporterId() { return reporterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getService() { return service; }
    public int getImpact() { return impact; }
    public int getUrgency() { return urgency; }
    public int getAffectedUsers() { return affectedUsers; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Category getCategory() { return category; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public String getTechnicianId() { return technicianId; }
    public LocalDateTime getResponseDue() { return responseDue; }
    public LocalDateTime getResolveDue() { return resolveDue; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getResolution() { return resolution; }
    public Feedback getFeedback() { return feedback; }
    public int getEscalationLevel() { return escalationLevel; }
    public List<Note> getNotes() { return Collections.unmodifiableList(notes); }
    public List<RelatedIncident> getRelated() { return Collections.unmodifiableList(related); }

    @Override public String toString() {
        return "%s [%s] %s | %s | %s | %s | tech=%s | SLA=%s".formatted(id, priority, title, service,
                category, status, technicianId == null ? "-" : technicianId, getSlaStatus());
    }
}
