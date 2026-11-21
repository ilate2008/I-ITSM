package com.iitsm.model;

import java.time.LocalDateTime;

/** State pattern: each lifecycle state owns its legal transitions and its entry behaviour. */
public interface IncidentState {
    Status status();
    boolean canTransitionTo(Status target);
    void onEnter(Incident incident, LocalDateTime now);
}
