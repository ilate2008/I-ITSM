package com.iitsm.service;

import com.iitsm.model.Category;

/** Input for reporting an incident. category may be null (it is then suggested automatically). */
public record IncidentRequest(String title, String description, String serviceName,
                              int impact, int urgency, int affectedUsers, Category category) {
    public IncidentRequest(String title, String description, String serviceName, int impact, int urgency, int affectedUsers) {
        this(title, description, serviceName, impact, urgency, affectedUsers, null);
    }
}
