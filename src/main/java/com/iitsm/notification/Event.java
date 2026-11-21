package com.iitsm.notification;

import java.time.LocalDateTime;
import java.util.List;

public record Event(String type, String incidentId, String actor, String oldStatus, String newStatus,
                    String description, List<String> recipients, LocalDateTime timestamp, String priority) { }
