package com.iitsm.notification;

import java.io.Serializable;
import java.time.LocalDateTime;

public record Notification(String userId, String channel, String subject, String body, LocalDateTime timestamp) implements Serializable { }
