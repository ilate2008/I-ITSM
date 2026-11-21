package com.iitsm.strategy;

import com.iitsm.model.Priority;

/** Strategy pattern: interchangeable priority algorithms. */
public interface PriorityStrategy {
    String name();
    Priority calculate(int impact, int urgency, int criticality, int affectedUsers);
}
