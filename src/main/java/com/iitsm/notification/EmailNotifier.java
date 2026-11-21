package com.iitsm.notification;

import com.iitsm.model.User;
import com.iitsm.repository.Repository;

/** Simulated e-mail: the message is stored as "sent". */
public class EmailNotifier extends Notifier {
    public EmailNotifier(Repository repo) { super(repo); }
    @Override public String channel() { return "email"; }
    @Override protected String format(User u, String subject, String body) {
        return "To: " + u.getEmail() + " | Subject: " + subject + " | " + body;
    }
}
