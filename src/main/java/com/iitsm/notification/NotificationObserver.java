package com.iitsm.notification;

import com.iitsm.model.User;
import com.iitsm.repository.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Channel selection: always in-app; P1/P2 also e-mail; SLA violations and escalations also SMS. */
public class NotificationObserver implements EventObserver {
    private final Repository repo;
    private final Map<String, Notifier> notifiers = new java.util.HashMap<>();

    public NotificationObserver(Repository repo) {
        this.repo = repo;
        for (String ch : List.of("in_app", "email", "sms")) notifiers.put(ch, NotifierFactory.create(ch, repo));
    }

    List<String> channelsFor(Event e) {
        List<String> channels = new ArrayList<>(List.of("in_app"));
        if ("P1".equals(e.priority()) || "P2".equals(e.priority())) channels.add("email");
        if (e.type().equals("SLA_VIOLATED") || e.type().equals("INCIDENT_ESCALATED")) channels.add("sms");
        return channels;
    }

    @Override public void update(Event e) {
        String subject = "[" + e.incidentId() + "] " + e.type();
        for (String uid : e.recipients()) {
            Optional<User> user = repo.findUser(uid);
            if (user.isEmpty()) continue;
            for (String ch : channelsFor(e)) notifiers.get(ch).send(user.get(), subject, e.description(), e.timestamp());
        }
    }
}
