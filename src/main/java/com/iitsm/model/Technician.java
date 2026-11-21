package com.iitsm.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Technician extends User {
    private final Set<Category> skills;
    private final Map<String, Integer> serviceExperience;
    private final int experienceYears;
    private final int maxLoad;
    private boolean available = true;

    public Technician(String id, String name, String email, String passwordHash, TechnicianProfile profile) {
        super(id, name, email, passwordHash);
        this.skills = profile.skills().isEmpty() ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(profile.skills());
        this.serviceExperience = new HashMap<>(profile.serviceExperience());
        this.experienceYears = profile.experienceYears();
        this.maxLoad = profile.maxLoad();
    }

    @Override public Role role() { return Role.TECHNICIAN; }

    public Set<Category> getSkills() { return Collections.unmodifiableSet(skills); }
    public Map<String, Integer> getServiceExperience() { return Collections.unmodifiableMap(serviceExperience); }
    public int experienceWith(String service) { return serviceExperience.getOrDefault(service, 0); }
    public int getExperienceYears() { return experienceYears; }
    public int getMaxLoad() { return maxLoad; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean hasSkill(Category c) { return skills.contains(c); }
}
