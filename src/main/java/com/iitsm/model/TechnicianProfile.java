package com.iitsm.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public record TechnicianProfile(Set<Category> skills, int experienceYears, int maxLoad,
                                Map<String, Integer> serviceExperience) implements Serializable {
    public static TechnicianProfile of(int experienceYears, int maxLoad, Category... skills) {
        return new TechnicianProfile(Set.of(skills), experienceYears, maxLoad, Map.of());
    }
    public static TechnicianProfile of(int experienceYears, int maxLoad, Map<String, Integer> serviceExperience, Category... skills) {
        return new TechnicianProfile(Set.of(skills), experienceYears, maxLoad, serviceExperience);
    }
}
