package com.iitsm.notification;

/** Observer that forwards only the important events (P1, SLA violations, escalations) to an external {@link AlertSink}. */
public class AlertSinkObserver implements EventObserver {
    private final AlertSink sink;

    public AlertSinkObserver(AlertSink sink) { this.sink = sink; }

    static boolean isImportant(Event e) {
        return "P1".equals(e.priority()) || e.type().equals("SLA_VIOLATED") || e.type().equals("INCIDENT_ESCALATED");
    }

    @Override public void update(Event e) {
        if (isImportant(e)) sink.publish("[" + e.incidentId() + "] " + e.type(), e.description());
    }
}
