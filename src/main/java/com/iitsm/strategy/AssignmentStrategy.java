package com.iitsm.strategy;

import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.exception.WorkloadExceededException;
import com.iitsm.model.Incident;
import com.iitsm.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Strategy + Template Method: common eligibility checks, subclass-specific choice. */
public abstract class AssignmentStrategy {
    public abstract String name();

    public final Technician select(Incident incident, List<Technician> technicians, Map<String, Integer> loads) {
        List<Technician> available = technicians.stream().filter(Technician::isAvailable).toList();
        if (available.isEmpty()) throw new TechnicianUnavailableException("No technician is available");
        List<Technician> free = available.stream()
                .filter(t -> loads.getOrDefault(t.getId(), 0) < t.getMaxLoad())
                .sorted(Comparator.comparing(Technician::getId)).toList();
        if (free.isEmpty()) throw new WorkloadExceededException("All available technicians are at maximum workload");
        return choose(incident, free, loads);
    }

    protected abstract Technician choose(Incident incident, List<Technician> candidates, Map<String, Integer> loads);
}
