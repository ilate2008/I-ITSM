package com.iitsm.strategy;

import com.iitsm.model.Incident;
import com.iitsm.model.Technician;
import java.util.List;
import java.util.Map;

public class RoundRobinAssignment extends AssignmentStrategy {
    private String lastId;
    @Override public String name() { return "round_robin"; }
    @Override protected Technician choose(Incident incident, List<Technician> candidates, Map<String, Integer> loads) {
        Technician next = candidates.stream().filter(t -> lastId == null || t.getId().compareTo(lastId) > 0)
                .findFirst().orElse(candidates.get(0));
        lastId = next.getId();
        return next;
    }
}
