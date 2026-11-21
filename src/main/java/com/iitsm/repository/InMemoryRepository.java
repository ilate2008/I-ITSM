package com.iitsm.repository;

import com.iitsm.model.ITService;
import com.iitsm.model.Incident;
import com.iitsm.model.Role;
import com.iitsm.model.User;
import com.iitsm.notification.AuditRecord;
import com.iitsm.notification.Notification;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Collections-backed repository; subclasses hook {@link #onChange()} to persist. */
public class InMemoryRepository implements Repository {
    /** All persistent state in one serialisable holder. */
    protected static class Data implements Serializable {
        final Map<String, User> users = new TreeMap<>();
        final Map<String, ITService> services = new TreeMap<>();
        final Map<String, Incident> incidents = new TreeMap<>();
        final List<AuditRecord> audit = new ArrayList<>();
        final List<Notification> notifications = new ArrayList<>();
        int incidentCounter = 0;
    }

    protected Data data = new Data();

    protected void onChange() { }

    public synchronized boolean isEmpty() { return data.users.isEmpty(); }

    @Override public synchronized void saveUser(User u) { data.users.put(u.getId(), u); onChange(); }
    @Override public synchronized Optional<User> findUser(String id) { return Optional.ofNullable(data.users.get(id)); }
    @Override public synchronized Optional<User> findUserByEmail(String email) {
        return data.users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email == null ? "" : email.trim())).findFirst();
    }
    @Override public synchronized List<User> listUsers(Role role) {
        return data.users.values().stream().filter(u -> role == null || u.role() == role).toList();
    }

    @Override public synchronized void saveService(ITService s) { data.services.put(s.getName(), s); onChange(); }
    @Override public synchronized Optional<ITService> findService(String name) {
        return data.services.values().stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst();
    }
    @Override public synchronized List<ITService> listServices() { return new ArrayList<>(data.services.values()); }

    @Override public synchronized String nextIncidentId() { return "INC" + (1001 + data.incidentCounter++); }
    @Override public synchronized void saveIncident(Incident i) { data.incidents.put(i.getId(), i); onChange(); }
    @Override public synchronized Optional<Incident> findIncident(String id) { return Optional.ofNullable(data.incidents.get(id == null ? "" : id.toUpperCase())); }
    @Override public synchronized List<Incident> findIncidents(IncidentQuery q) { return q.filter(data.incidents.values()); }

    @Override public synchronized void addAudit(AuditRecord r) { data.audit.add(r); onChange(); }
    @Override public synchronized List<AuditRecord> auditFor(String incidentId) {
        return data.audit.stream().filter(a -> incidentId == null || a.incidentId().equals(incidentId)).toList();
    }

    @Override public synchronized void addNotification(Notification n) { data.notifications.add(n); onChange(); }
    @Override public synchronized List<Notification> notificationsFor(String userId) {
        return data.notifications.stream().filter(n -> n.userId().equals(userId)).toList();
    }
}
