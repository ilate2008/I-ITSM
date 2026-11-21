package com.iitsm.notification;

import com.iitsm.exception.ValidationException;
import com.iitsm.repository.Repository;

/** Factory pattern for notification channels. */
public final class NotifierFactory {
    private NotifierFactory() { }
    public static Notifier create(String channel, Repository repo) {
        return switch (channel) {
            case "in_app" -> new InAppNotifier(repo);
            case "email" -> new EmailNotifier(repo);
            case "sms" -> new SmsNotifier(repo);
            default -> throw new ValidationException("Unknown notification channel " + channel);
        };
    }
}
