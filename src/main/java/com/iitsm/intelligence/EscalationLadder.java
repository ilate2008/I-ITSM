package com.iitsm.intelligence;

import java.util.List;

/** Intelligent feature C: escalation chain for unacknowledged critical incidents. */
public final class EscalationLadder {
    public static final List<String> LEVELS = List.of("Technician", "Team Lead", "System Administrator", "IT Manager");
    private EscalationLadder() { }
    public static int maxLevel() { return LEVELS.size() - 1; }
}
