package com.iitsm.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Adaptee: a "third-party" HTTP client with its own incompatible API (post a JSON body to a URL). */
public class HttpWebhookClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI url;

    public HttpWebhookClient(String url) { this.url = URI.create(url); }

    /** Fire-and-forget POST so a slow or failing network never blocks incident handling. */
    public void postJson(String json) {
        HttpRequest req = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> {
            System.err.println("[webhook] delivery failed: " + e.getMessage());
            return null;
        });
    }
}
