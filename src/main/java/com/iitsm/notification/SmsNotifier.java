package com.iitsm.notification;

import com.iitsm.model.User;
import com.iitsm.repository.Repository;

public class SmsNotifier extends Notifier {
    public SmsNotifier(Repository repo) { super(repo); }
    @Override public String channel() { return "sms"; }
    @Override protected String format(User u, String subject, String body) {
        String s = subject + ": " + body;
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
