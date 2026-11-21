package com.iitsm.strategy;

import com.iitsm.model.Incident;
import com.iitsm.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LoadBasedAssignment extends AssignmentStrategy {
    @Override public String name() { return "load"; }
    @Override protected Technician choose(Incident incident, List<Technician> candidates, Map<String, Integer> loads) {
        return candidates.stream().min(Comparator.comparingInt((Technician t) -> loads.getOrDefault(t.getId(), 0))
                .thenComparing(Technician::getId)).get();
    }
}
