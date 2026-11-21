package com.iitsm.model;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Registry of the concrete lifecycle states. */
public final class IncidentStates {
    private static final Map<Status, IncidentState> STATES = new EnumMap<>(Status.class);

    static {
        register(new Simple(Status.NEW, EnumSet.of(Status.CLASSIFIED)));
        register(new Simple(Status.CLASSIFIED, EnumSet.of(Status.ASSIGNED)));
        register(new Simple(Status.ASSIGNED, EnumSet.of(Status.ACKNOWLEDGED)));
        register(new Simple(Status.ACKNOWLEDGED, EnumSet.of(Status.IN_PROGRESS)) {
            @Override public void onEnter(Incident i, LocalDateTime now) { i.markAcknowledged(now); }
        });
        register(new Simple(Status.IN_PROGRESS, EnumSet.of(Status.RESOLVED)));
        register(new Simple(Status.RESOLVED, EnumSet.of(Status.VERIFIED, Status.IN_PROGRESS)) {
            @Override public void onEnter(Incident i, LocalDateTime now) { i.markResolved(now); }
        });
        register(new Simple(Status.VERIFIED, EnumSet.of(Status.CLOSED)));
        register(new Simple(Status.CLOSED, EnumSet.noneOf(Status.class)) {
            @Override public void onEnter(Incident i, LocalDateTime now) { i.markClosed(now); }
        });
    }

    private IncidentStates() { }

    private static void register(IncidentState s) { STATES.put(s.status(), s); }

    public static IncidentState of(Status status) { return STATES.get(status); }

    private static class Simple implements IncidentState {
        private final Status status;
        private final Set<Status> allowed;
        Simple(Status status, Set<Status> allowed) { this.status = status; this.allowed = allowed; }
        @Override public Status status() { return status; }
        @Override public boolean canTransitionTo(Status target) { return allowed.contains(target); }
        @Override public void onEnter(Incident incident, LocalDateTime now) { /* no-op by default */ }
    }
}
