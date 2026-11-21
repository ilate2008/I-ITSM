package com.iitsm.strategy;

import com.iitsm.model.Priority;

/** Weighted score: 0.4*impact + 0.3*urgency + 0.3*criticality (+0.2 for 100+ users). */
public class WeightedPriorityStrategy implements PriorityStrategy {
    @Override public String name() { return "weighted"; }
    @Override public Priority calculate(int impact, int urgency, int criticality, int affectedUsers) {
        double s = 0.4 * impact + 0.3 * urgency + 0.3 * criticality + (affectedUsers >= 100 ? 0.2 : 0);
        if (s >= 2.6) return Priority.P1;
        if (s >= 2.0) return Priority.P2;
        if (s >= 1.4) return Priority.P3;
        return Priority.P4;
    }
}
