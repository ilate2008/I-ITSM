package com.iitsm.notification;

/** Target interface of the Adapter pattern: what the application wants from any external alerting service. */
public interface AlertSink {
    void publish(String subject, String message);
}
