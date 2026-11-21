package com.iitsm.model;

import com.iitsm.exception.ValidationException;
import java.io.Serializable;

/** An IT service (e.g. Payment). Criticality 1..3; may carry its own SLA policy overriding the default. */
public class ITService implements Serializable {
    private final String name;
    private final int criticality;
    private final String description;
    private final SlaPolicy sla; // nullable

    public ITService(String name, int criticality, String description, SlaPolicy sla) {
        if (name == null || name.isBlank()) throw new ValidationException("Service name required");
        if (criticality < 1 || criticality > 3) throw new ValidationException("criticality must be 1..3");
        this.name = name; this.criticality = criticality; this.description = description; this.sla = sla;
    }

    public ITService(String name, int criticality) { this(name, criticality, "", null); }

    public String getName() { return name; }
    public int getCriticality() { return criticality; }
    public String getDescription() { return description; }
    public SlaPolicy getSla() { return sla; }
}
