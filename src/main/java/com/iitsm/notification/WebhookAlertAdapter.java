package com.iitsm.notification;

/** Adapter pattern: makes the webhook HTTP client usable wherever an {@link AlertSink} is expected. */
public class WebhookAlertAdapter implements AlertSink {
    private final HttpWebhookClient client;

    public WebhookAlertAdapter(HttpWebhookClient client) { this.client = client; }

    @Override public void publish(String subject, String message) {
        String text = "*" + subject + "*\n" + message;
        client.postJson("{\"text\":\"" + escape(text) + "\",\"content\":\"" + escape(text) + "\"}");   // Slack and Discord field names
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
