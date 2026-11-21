package com.iitsm.model;

import java.util.List;

public enum Status {
    NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, VERIFIED, CLOSED;

    /** Statuses that count toward a technician's workload. */
    public static List<Status> activeWork() { return List.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS); }
    /** Statuses in which the incident is still being handled. */
    public static List<Status> open() { return List.of(NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS); }
}
