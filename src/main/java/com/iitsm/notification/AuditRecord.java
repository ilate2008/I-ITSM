package com.iitsm.notification;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record AuditRecord(LocalDateTime timestamp, String user, String action, String incidentId,
                          String oldStatus, String newStatus, String description) implements Serializable {
    @Override public String toString() {
        String arrow = oldStatus.equals(newStatus) ? "" : " [" + oldStatus + "->" + newStatus + "]";
        return "%s %s %s by %s%s: %s".formatted(timestamp.format(DateTimeFormatter.ofPattern("dd-MM HH:mm:ss")),
                incidentId, action, user, arrow, description);
    }
}
