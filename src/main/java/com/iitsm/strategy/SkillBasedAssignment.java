package com.iitsm.strategy;

import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.model.Incident;
import com.iitsm.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Picks the skilled technician with the best mix of service experience, years of experience and low load. */
public class SkillBasedAssignment extends AssignmentStrategy {
    @Override public String name() { return "skill"; }
    @Override protected Technician choose(Incident incident, List<Technician> candidates, Map<String, Integer> loads) {
        List<Technician> skilled = candidates.stream().filter(t -> t.hasSkill(incident.getCategory())).toList();
        if (skilled.isEmpty()) throw new TechnicianUnavailableException("No technician has skill " + incident.getCategory());
        return skilled.stream().max(Comparator.comparingDouble(t ->
                t.experienceWith(incident.getService()) * 2.0 + t.getExperienceYears() - 0.5 * loads.getOrDefault(t.getId(), 0))).get();
    }
}
