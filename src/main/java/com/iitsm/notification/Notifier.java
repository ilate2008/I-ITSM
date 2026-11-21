package com.iitsm.notification;

import com.iitsm.model.User;
import com.iitsm.repository.Repository;
import java.time.LocalDateTime;

/** Abstraction for a delivery channel; new channels only need to extend this class. */
public abstract class Notifier {
    protected final Repository repo;
    protected Notifier(Repository repo) { this.repo = repo; }
    public abstract String channel();
    protected abstract String format(User user, String subject, String body);

    public void send(User user, String subject, String body, LocalDateTime when) {
        repo.addNotification(new Notification(user.getId(), channel(), subject, format(user, subject, body), when));
    }
}
