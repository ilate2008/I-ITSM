package com.iitsm.service;

import com.iitsm.exception.*;
import com.iitsm.intelligence.Categorizer;
import com.iitsm.intelligence.DuplicateDetector;
import com.iitsm.intelligence.EscalationLadder;
import com.iitsm.intelligence.SlaRiskDetector;
import com.iitsm.model.*;
import com.iitsm.notification.*;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.Repository;
import com.iitsm.strategy.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Application facade: authentication, authorisation, the incident workflow, SLA monitoring and reporting. */
public class IncidentService {
    private final Repository repo;
    private final Supplier<LocalDateTime> clock;
    private final EventBus bus = new EventBus();
    private final Categorizer categorizer = new Categorizer();
    private final DuplicateDetector detector = new DuplicateDetector();
    private final SlaRiskDetector risk = new SlaRiskDetector();
    private final SlaPolicy defaultSla;
    private PriorityStrategy priorityStrategy = new ScorePriorityStrategy();
    private AssignmentStrategy assignmentStrategy = new SkillBasedAssignment();

    public IncidentService(Repository repo, Supplier<LocalDateTime> clock) { this(repo, clock, repo.loadDefaultSla().orElseGet(SlaPolicy::defaults)); }

    public IncidentService(Repository repo, Supplier<LocalDateTime> clock, SlaPolicy defaultSla) {
        this.repo = repo; this.clock = clock; this.defaultSla = defaultSla;
        bus.subscribe(new AuditObserver(repo));
        bus.subscribe(new NotificationObserver(repo));
    }

    /** Observer pattern: lets the application plug extra observers (e.g. an external alert adapter) in without touching this class. */
    public void subscribe(EventObserver observer) { bus.subscribe(observer); }

    public Categorizer categorizer() { return categorizer; }
    public AssignmentStrategy assignmentStrategy() { return assignmentStrategy; }
    public PriorityStrategy priorityStrategy() { return priorityStrategy; }

    // ------------------------------------------------------------------ security & administration
    public User login(String email, String password) {
        return repo.findUserByEmail(email).filter(u -> u.checkPassword(password))
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));
    }

    private void require(User actor, Permission p) {
        if (actor == null || !Permission.isGranted(actor.role(), p)) {
            throw new AuthorizationException((actor == null ? "anonymous" : actor.role()) + " may not perform " + p);
        }
    }

    public Employee registerEmployee(String name, String email, String password) {
        if (repo.findUserByEmail(email).isPresent()) throw new ValidationException("Email already registered");
        Employee e = (Employee) UserFactory.create(Role.EMPLOYEE, nextUserId(Role.EMPLOYEE), name, email, password, null);
        repo.saveUser(e);
        return e;
    }

    public User addUser(User actor, Role role, String name, String email, String password, TechnicianProfile profile) {
        require(actor, Permission.MANAGE_USERS);
        if (repo.findUserByEmail(email).isPresent()) throw new ValidationException("Email already registered");
        User u = UserFactory.create(role, nextUserId(role), name, email, password, profile);
        repo.saveUser(u);
        return u;
    }

    private String nextUserId(Role role) {
        String prefix = switch (role) { case EMPLOYEE -> "E"; case TECHNICIAN -> "T"; case ADMIN -> "A"; };
        return prefix + (101 + repo.listUsers(role).size());
    }

    public void setTechnicianAvailability(User actor, String technicianId, boolean available) {
        require(actor, Permission.MANAGE_USERS);
        User u = repo.findUser(technicianId).orElseThrow(() -> new ValidationException("Unknown technician"));
        if (!(u instanceof Technician t)) throw new ValidationException(technicianId + " is not a technician");
        t.setAvailable(available);
        repo.saveUser(t);
    }

    public void addService(User actor, ITService service) { require(actor, Permission.MANAGE_SERVICES); repo.saveService(service); }
    public void configureSla(User actor, Priority p, int responseMin, int resolutionMin) {
        require(actor, Permission.CONFIGURE_SLA); defaultSla.setTarget(p, responseMin, resolutionMin);
        repo.saveDefaultSla(defaultSla);
    }
    public void setAssignmentStrategy(User actor, AssignmentStrategy s) { require(actor, Permission.CONFIGURE_STRATEGY); assignmentStrategy = s; }
    public void setPriorityStrategy(User actor, PriorityStrategy s) { require(actor, Permission.CONFIGURE_STRATEGY); priorityStrategy = s; }

    // ------------------------------------------------------------------ helpers
    private Incident get(String id) {
        return repo.findIncident(id).orElseThrow(() -> new InvalidIncidentException("Incident " + id + " not found"));
    }

    private Map<String, Integer> loads() {
        Map<String, Integer> m = new HashMap<>();
        for (Incident i : repo.findIncidents(IncidentQuery.all().statuses(Status.activeWork()))) m.merge(i.getTechnicianId(), 1, Integer::sum);
        return m;
    }

    private boolean canView(User actor, Incident inc) {
        return switch (actor.role()) {
            case ADMIN -> true;
            case EMPLOYEE -> inc.getReporterId().equals(actor.getId());
            case TECHNICIAN -> actor.getId().equals(inc.getTechnicianId());
        };
    }

    private void requireAssignedTechnician(User actor, Incident inc) {
        if (actor.role() != Role.TECHNICIAN || !actor.getId().equals(inc.getTechnicianId())) {
            throw new AuthorizationException("Only the assigned technician may do this");
        }
    }

    private void requireReporterOrAdmin(User actor, Incident inc) {
        if (actor.role() == Role.EMPLOYEE && !inc.getReporterId().equals(actor.getId())) throw new AuthorizationException("Not your incident");
    }

    private List<String> recipients(String type, Incident inc) {
        Set<String> r = new TreeSet<>();
        if (Set.of("INCIDENT_CREATED", "INCIDENT_ASSIGNED", "INCIDENT_RESOLVED", "INCIDENT_CLOSED", "INFO_REQUESTED", "INCIDENT_VERIFIED").contains(type)) r.add(inc.getReporterId());
        if (Set.of("INCIDENT_ASSIGNED", "SLA_WARNING", "SLA_VIOLATED", "INCIDENT_ESCALATED").contains(type) && inc.getTechnicianId() != null) r.add(inc.getTechnicianId());
        if (Set.of("SLA_WARNING", "SLA_VIOLATED", "INCIDENT_ESCALATED", "DUPLICATE_SUSPECTED", "ASSIGNMENT_FAILED").contains(type)) {
            repo.listUsers(Role.ADMIN).forEach(a -> r.add(a.getId()));
        }
        return new ArrayList<>(r);
    }

    private void emit(String type, Incident inc, String actor, String description, Status from, Status to) {
        bus.publish(new Event(type, inc.getId(), actor, (from == null ? inc.getStatus() : from).name(),
                (to == null ? inc.getStatus() : to).name(), description, recipients(type, inc), clock.get(),
                inc.getPriority() == null ? "" : inc.getPriority().name()));
    }

    private void emit(String type, Incident inc, String actor, String description) { emit(type, inc, actor, description, null, null); }

    private static final Map<Status, String> STATUS_EVENT = Map.of(
            Status.CLASSIFIED, "INCIDENT_CLASSIFIED", Status.ASSIGNED, "INCIDENT_ASSIGNED",
            Status.ACKNOWLEDGED, "INCIDENT_ACKNOWLEDGED", Status.IN_PROGRESS, "INVESTIGATION_STARTED",
            Status.RESOLVED, "INCIDENT_RESOLVED", Status.VERIFIED, "INCIDENT_VERIFIED", Status.CLOSED, "INCIDENT_CLOSED");

    /** Applies a lifecycle transition through the State objects, persists it and publishes the event. */
    private void transition(Incident inc, Status target, String actor, String description) {
        Status old = inc.getStatus();
        inc.transitionTo(target, clock.get());
        repo.saveIncident(inc);
        emit(STATUS_EVENT.get(target), inc, actor, description, old, target);
    }

    private SlaTarget slaFor(ITService service, Priority p) {
        if (service.getSla() != null && service.getSla().covers(p)) return service.getSla().targetsFor(p);
        return defaultSla.targetsFor(p);
    }

    // ------------------------------------------------------------------ incident workflow
    public Incident createIncident(User actor, IncidentRequest r) {
        require(actor, Permission.CREATE_INCIDENT);
        if (r.title() == null || r.title().isBlank() || r.description() == null || r.description().isBlank())
            throw new ValidationException("Title and description are required");
        if (r.impact() < 1 || r.impact() > 3 || r.urgency() < 1 || r.urgency() > 3 || r.affectedUsers() < 1)
            throw new ValidationException("impact/urgency must be 1..3 and affected users >= 1");
        ITService service = repo.findService(r.serviceName())
                .orElseThrow(() -> new ServiceNotFoundException("Service '" + r.serviceName() + "' not found"));
        List<Incident> open = repo.findIncidents(IncidentQuery.all().statuses(
                List.of(Status.NEW, Status.CLASSIFIED, Status.ASSIGNED, Status.ACKNOWLEDGED, Status.IN_PROGRESS, Status.RESOLVED)));
        for (Incident o : open) {
            if (o.getReporterId().equals(actor.getId()) && o.getService().equals(service.getName())
                    && o.getTitle().equalsIgnoreCase(r.title().trim()))
                throw new DuplicateIncidentException("You already reported this incident: " + o.getId());
        }
        LocalDateTime now = clock.get();
        Incident inc = new Incident(repo.nextIncidentId(), actor.getId(), r.title().trim(), r.description().trim(),
                service.getName(), r.impact(), r.urgency(), r.affectedUsers(), now);
        inc.setRelated(detector.findSimilar(inc, open));
        repo.saveIncident(inc);
        emit("INCIDENT_CREATED", inc, actor.getId(), "Incident created by " + actor.role() + " " + actor.getId() + ": " + inc.getTitle());
        if (!inc.getRelated().isEmpty()) {
            emit("DUPLICATE_SUSPECTED", inc, "system", "Possibly related to " + inc.getRelated().stream()
                    .map(x -> x.incidentId() + " (" + x.score() + ")").collect(Collectors.joining(", ")));
        }
        Category cat = r.category() != null ? r.category() : categorizer.suggest(r.title() + " " + r.description());
        inc.setCategory(cat);
        transition(inc, Status.CLASSIFIED, "system", "Incident classified as " + cat);
        Priority p = priorityStrategy.calculate(r.impact(), r.urgency(), service.getCriticality(), r.affectedUsers());
        SlaTarget t = slaFor(service, p);
        inc.setPriority(p, now.plus(t.response()), now.plus(t.resolution()));
        repo.saveIncident(inc);
        emit("PRIORITY_CALCULATED", inc, "system", "Priority " + p + " (" + p.label() + "); response due " + inc.getResponseDue().toLocalTime()
                + ", resolution due " + inc.getResolveDue().toLocalTime());
        try {
            autoAssign(inc, "system");
        } catch (TechnicianUnavailableException e) {
            emit("ASSIGNMENT_FAILED", inc, "system", "Auto-assignment failed: " + e.getMessage());
        }
        return inc;
    }

    private void autoAssign(Incident inc, String actor) {
        List<Technician> techs = repo.listUsers(Role.TECHNICIAN).stream().map(u -> (Technician) u).toList();
        Technician t = assignmentStrategy.select(inc, techs, loads());
        inc.assignTo(t.getId());
        transition(inc, Status.ASSIGNED, actor, "Assigned to " + t.getId() + " (" + t.getName() + ") via " + assignmentStrategy.name() + " strategy");
    }

    /** Manual assignment by an administrator; if technicianId is null the current strategy chooses. */
    public Incident assign(User actor, String incidentId, String technicianId) {
        require(actor, Permission.ASSIGN);
        Incident inc = get(incidentId);
        if (!inc.canTransitionTo(Status.ASSIGNED)) throw new InvalidStateTransitionException("Cannot assign an incident in state " + inc.getStatus());
        if (technicianId == null) {
            autoAssign(inc, actor.getId());
        } else {
            Technician t = repo.findUser(technicianId).filter(u -> u instanceof Technician).map(u -> (Technician) u)
                    .orElseThrow(() -> new ValidationException("Unknown technician " + technicianId));
            if (!t.isAvailable()) throw new TechnicianUnavailableException(technicianId + " is not available");
            if (loads().getOrDefault(technicianId, 0) >= t.getMaxLoad()) throw new WorkloadExceededException(technicianId + " is at maximum workload");
            inc.assignTo(technicianId);
            transition(inc, Status.ASSIGNED, actor.getId(), "Manually assigned to " + technicianId);
        }
        return inc;
    }

    public Incident acknowledge(User actor, String id) {
        require(actor, Permission.ACKNOWLEDGE); Incident inc = get(id); requireAssignedTechnician(actor, inc);
        transition(inc, Status.ACKNOWLEDGED, actor.getId(), "Technician acknowledged incident");
        return inc;
    }

    public Incident startInvestigation(User actor, String id) {
        require(actor, Permission.INVESTIGATE); Incident inc = get(id); requireAssignedTechnician(actor, inc);
        transition(inc, Status.IN_PROGRESS, actor.getId(), "Investigation started");
        return inc;
    }

    public Incident addNote(User actor, String id, String text) {
        Incident inc = get(id);
        switch (actor.role()) {
            case EMPLOYEE -> { require(actor, Permission.ADD_INFO); requireReporterOrAdmin(actor, inc); }
            case TECHNICIAN -> { require(actor, Permission.ADD_NOTE); requireAssignedTechnician(actor, inc); }
            case ADMIN -> require(actor, Permission.ADD_NOTE);
        }
        if (text == null || text.isBlank()) throw new ValidationException("Note cannot be empty");
        inc.addNote(new Note(clock.get(), actor.getId(), "note", text.trim()));
        repo.saveIncident(inc);
        emit("NOTE_ADDED", inc, actor.getId(), "note: " + text.trim());
        return inc;
    }

    public void requestInfo(User actor, String id, String question) {
        require(actor, Permission.REQUEST_INFO); Incident inc = get(id); requireAssignedTechnician(actor, inc);
        if (question == null || question.isBlank()) throw new ValidationException("Question cannot be empty");
        inc.addNote(new Note(clock.get(), actor.getId(), "info_request", question.trim()));
        repo.saveIncident(inc);
        emit("INFO_REQUESTED", inc, actor.getId(), "More information requested: " + question.trim());
    }

    public Incident resolve(User actor, String id, String resolution) {
        require(actor, Permission.RESOLVE); Incident inc = get(id); requireAssignedTechnician(actor, inc);
        if (resolution == null || resolution.isBlank()) throw new ValidationException("Resolution details are required");
        if (!inc.canTransitionTo(Status.RESOLVED)) throw new InvalidStateTransitionException(inc.getStatus() + " -> RESOLVED is not allowed");
        inc.setResolution(resolution.trim());
        transition(inc, Status.RESOLVED, actor.getId(), "Resolution provided: " + resolution.trim());
        return inc;
    }

    /** Reporter (or admin) accepts the fix (VERIFIED) or rejects it (back to IN_PROGRESS). */
    public Incident verify(User actor, String id, boolean accepted) {
        require(actor, Permission.VERIFY); Incident inc = get(id); requireReporterOrAdmin(actor, inc);
        if (accepted) transition(inc, Status.VERIFIED, actor.getId(), "Resolution verified by " + actor.getId());
        else transition(inc, Status.IN_PROGRESS, actor.getId(), "Resolution rejected; incident reopened");
        return inc;
    }

    public Incident close(User actor, String id) {
        require(actor, Permission.CLOSE); Incident inc = get(id); requireReporterOrAdmin(actor, inc);
        transition(inc, Status.CLOSED, actor.getId(), "Incident closed");
        return inc;
    }

    public void giveFeedback(User actor, String id, int rating, String comment) {
        require(actor, Permission.FEEDBACK); Incident inc = get(id);
        if (!inc.getReporterId().equals(actor.getId())) throw new AuthorizationException("Not your incident");
        if (inc.getStatus() != Status.VERIFIED && inc.getStatus() != Status.CLOSED)
            throw new InvalidStateTransitionException("Feedback is only possible after the resolution is verified");
        if (rating < 1 || rating > 5) throw new ValidationException("Rating must be 1..5");
        inc.setFeedback(new Feedback(rating, comment == null ? "" : comment));
        repo.saveIncident(inc);
        emit("FEEDBACK_RECEIVED", inc, actor.getId(), "Feedback " + rating + "/5 " + (comment == null ? "" : comment));
    }

    // ------------------------------------------------------------------ SLA monitoring & escalation
    /** Intended to be called periodically (see SlaMonitor). Emits warnings, violations and escalations exactly once. */
    public synchronized List<SlaAlert> checkSla(User actorOrNull) {
        if (actorOrNull != null) require(actorOrNull, Permission.MONITOR_SLA);
        LocalDateTime now = clock.get();
        List<SlaAlert> alerts = new ArrayList<>();
        for (Incident inc : repo.findIncidents(IncidentQuery.all().statuses(Status.open()))) {
            if (inc.getPriority() == null) continue;
            boolean changed = false;
            if (inc.getAcknowledgedAt() == null && !inc.hasFlag(SlaFlag.RESPONSE_VIOLATED)) {
                if (now.isAfter(inc.getResponseDue())) {
                    inc.addSlaFlag(SlaFlag.RESPONSE_VIOLATED); changed = true;
                    emit("SLA_VIOLATED", inc, "system", "Response SLA violated"); alerts.add(new SlaAlert(inc.getId(), "RESPONSE_VIOLATED"));
                } else if (!inc.hasFlag(SlaFlag.RESPONSE_WARNING) && risk.atRisk(inc.getCreatedAt(), inc.getResponseDue(), now)) {
                    inc.addSlaFlag(SlaFlag.RESPONSE_WARNING); changed = true;
                    emit("SLA_WARNING", inc, "system", "Response deadline approaching"); alerts.add(new SlaAlert(inc.getId(), "RESPONSE_WARNING"));
                }
            }
            if (!inc.hasFlag(SlaFlag.RESOLUTION_VIOLATED)) {
                if (now.isAfter(inc.getResolveDue())) {
                    inc.addSlaFlag(SlaFlag.RESOLUTION_VIOLATED); changed = true;
                    emit("SLA_VIOLATED", inc, "system", "Resolution SLA violated"); alerts.add(new SlaAlert(inc.getId(), "RESOLUTION_VIOLATED"));
                } else if (!inc.hasFlag(SlaFlag.RESOLUTION_WARNING) && risk.atRisk(inc.getCreatedAt(), inc.getResolveDue(), now)) {
                    inc.addSlaFlag(SlaFlag.RESOLUTION_WARNING); changed = true;
                    emit("SLA_WARNING", inc, "system", "Resolution deadline approaching (SLA risk)"); alerts.add(new SlaAlert(inc.getId(), "RESOLUTION_WARNING"));
                }
            }
            if (inc.getPriority() == Priority.P1 && inc.getAcknowledgedAt() == null && now.isAfter(inc.getResponseDue())) {
                long window = Math.max(Duration.between(inc.getCreatedAt(), inc.getResponseDue()).toMillis(), 60_000);
                int target = Math.min(EscalationLadder.maxLevel(), 1 + (int) (Duration.between(inc.getResponseDue(), now).toMillis() / window));
                if (target > inc.getEscalationLevel()) {
                    inc.setEscalationLevel(target); changed = true;
                    String level = EscalationLadder.LEVELS.get(target);
                    emit("INCIDENT_ESCALATED", inc, "system", "Escalated to " + level);
                    alerts.add(new SlaAlert(inc.getId(), "ESCALATED_TO_" + level.toUpperCase().replace(' ', '_')));
                }
            }
            if (changed) repo.saveIncident(inc);
        }
        return alerts;
    }

    // ------------------------------------------------------------------ queries & reports
    public Incident viewIncident(User actor, String id) {
        Incident inc = get(id);
        if (!canView(actor, inc)) throw new AuthorizationException("You may not view this incident");
        return inc;
    }

    public List<Incident> search(User actor, IncidentQuery query) {
        require(actor, Permission.SEARCH);
        IncidentQuery q = switch (actor.role()) {
            case EMPLOYEE -> query.scopedTo(actor.getId(), null);
            case TECHNICIAN -> query.scopedTo(null, actor.getId());
            case ADMIN -> query;
        };
        return repo.findIncidents(q);
    }

    public List<AuditRecord> auditLog(User actor, String incidentId) {
        Incident inc = get(incidentId);
        if (!canView(actor, inc)) throw new AuthorizationException("You may not view this audit trail");
        return repo.auditFor(inc.getId());
    }

    public List<AuditRecord> fullAudit(User actor) { require(actor, Permission.AUDIT_ALL); return repo.auditFor(null); }

    public List<Notification> notifications(User actor) { return repo.notificationsFor(actor.getId()); }

    public Overview overview(User actor) {
        require(actor, Permission.REPORTS);
        List<Incident> all = repo.findIncidents(IncidentQuery.all());
        return new Overview(all.size(), (int) all.stream().filter(i -> Status.open().contains(i.getStatus())).count(),
                (int) all.stream().filter(i -> i.getPriority() == Priority.P1).count(),
                (int) all.stream().filter(i -> i.getStatus() == Status.RESOLVED || i.getStatus() == Status.VERIFIED || i.getStatus() == Status.CLOSED).count(),
                (int) all.stream().filter(i -> i.getSlaStatus().equals("BREACHED")).count());
    }

    public Map<String, Integer> technicianWorkload(User actor) {
        if (actor.role() == Role.EMPLOYEE) throw new AuthorizationException("Employees may not view workload");
        Map<String, Integer> l = loads(), out = new TreeMap<>();
        repo.listUsers(Role.TECHNICIAN).forEach(t -> out.put(t.getId(), l.getOrDefault(t.getId(), 0)));
        return out;
    }

    public Map<String, Integer> serviceAnalysis(User actor) {
        require(actor, Permission.REPORTS);
        Map<String, Integer> m = new TreeMap<>();
        repo.findIncidents(IncidentQuery.all()).forEach(i -> m.merge(i.getService(), 1, Integer::sum));
        return m;
    }
}
