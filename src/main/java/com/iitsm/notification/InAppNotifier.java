package com.iitsm.notification;

import com.iitsm.model.User;
import com.iitsm.repository.Repository;

public class InAppNotifier extends Notifier {
    public InAppNotifier(Repository repo) { super(repo); }
    @Override public String channel() { return "in_app"; }
    @Override protected String format(User u, String subject, String body) { return body; }
}
