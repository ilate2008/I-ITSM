package com.iitsm.strategy;

import com.iitsm.model.Priority;

/** score = impact x urgency x criticality (1..27); x1.5 when 100+ users are affected. P1>=18, P2>=9, P3>=4, else P4. */
public class ScorePriorityStrategy implements PriorityStrategy {
    @Override public String name() { return "score"; }
    @Override public Priority calculate(int impact, int urgency, int criticality, int affectedUsers) {
        int score = impact * urgency * criticality;
        if (affectedUsers >= 100) score = (int) (score * 1.5);
        if (score >= 18) return Priority.P1;
        if (score >= 9) return Priority.P2;
        if (score >= 4) return Priority.P3;
        return Priority.P4;
    }
}
