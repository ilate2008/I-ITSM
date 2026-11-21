package com.iitsm.repository;

import com.iitsm.model.ITService;
import com.iitsm.model.Incident;
import com.iitsm.model.Role;
import com.iitsm.model.User;
import com.iitsm.notification.AuditRecord;
import com.iitsm.notification.Notification;
import java.util.List;
import java.util.Optional;

/** Repository pattern: the domain/service layer only knows this contract, never how data is stored. */
public interface Repository {
    void saveUser(User user);
    Optional<User> findUser(String id);
    Optional<User> findUserByEmail(String email);
    List<User> listUsers(Role roleOrNull);

    void saveService(ITService service);
    Optional<ITService> findService(String name);
    List<ITService> listServices();

    String nextIncidentId();
    void saveIncident(Incident incident);
    Optional<Incident> findIncident(String id);
    List<Incident> findIncidents(IncidentQuery query);

    void addAudit(AuditRecord record);
    List<AuditRecord> auditFor(String incidentIdOrNull);

    /** Default (global) SLA policy. Optional: repositories that cannot store it keep the built-in defaults. */
    default void saveDefaultSla(com.iitsm.model.SlaPolicy policy) { }
    default Optional<com.iitsm.model.SlaPolicy> loadDefaultSla() { return Optional.empty(); }

    void addNotification(Notification notification);
    List<Notification> notificationsFor(String userId);
}
