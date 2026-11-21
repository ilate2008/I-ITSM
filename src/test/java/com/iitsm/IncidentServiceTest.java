package com.iitsm;

import static org.junit.jupiter.api.Assertions.*;

import com.iitsm.exception.*;
import com.iitsm.intelligence.Categorizer;
import com.iitsm.model.*;
import com.iitsm.repository.FileRepository;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.InMemoryRepository;
import com.iitsm.service.*;
import com.iitsm.strategy.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentServiceTest {
    InMemoryRepository repo;
    ManualClock clock;
    IncidentService svc;
    User admin, emp, emp2, t1, t2;
    int n;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepository();
        clock = new ManualClock();
        svc = new IncidentService(repo, clock);
        admin = UserFactory.create(Role.ADMIN, "A101", "Admin", "a@x.com", "AdminPass1", null);
        repo.saveUser(admin);
        t1 = svc.addUser(admin, Role.TECHNICIAN, "T1", "t1@x.com", "TechPass123", TechnicianProfile.of(5, 1, Category.APPLICATION, Category.NETWORK));
        t2 = svc.addUser(admin, Role.TECHNICIAN, "T2", "t2@x.com", "TechPass123", TechnicianProfile.of(1, 2, Category.NETWORK));
        svc.addService(admin, new ITService("Payment", 3));
        svc.addService(admin, new ITService("Network", 2));
        svc.addService(admin, new ITService("Wiki", 1));
        emp = svc.registerEmployee("Eva", "eva@x.com", "EmpPass123");
        emp2 = svc.registerEmployee("Raj", "raj@x.com", "EmpPass123");
    }

    Incident report(User u, String service, int impact, int urgency, int users, Category cat) {
        return svc.createIncident(u, new IncidentRequest("Issue number " + (++n) + " on " + service, "something broke " + n, service, impact, urgency, users, cat));
    }
    Incident payment() { return svc.createIncident(emp, new IncidentRequest("Payment app down", "transactions fail", "Payment", 3, 3, 10)); }
    Incident network() { return report(emp, "Network", 3, 3, 10, Category.NETWORK); }
    User tech(Incident i) { return repo.findUser(i.getTechnicianId()).get(); }

    // ---------- workflow & business rules
    @Test void t01_fullLifecycle() {
        Incident i = payment(); User t = tech(i);
        svc.acknowledge(t, i.getId()); svc.startInvestigation(t, i.getId()); svc.resolve(t, i.getId(), "restarted");
        svc.verify(emp, i.getId(), true); svc.close(emp, i.getId());
        assertEquals(Status.CLOSED, svc.viewIncident(admin, i.getId()).getStatus());
        assertNotNull(i.getClosedAt());
    }
    @Test void t02_autoPipelineSetsFields() {
        Incident i = payment();
        assertEquals(Category.APPLICATION, i.getCategory()); assertEquals(Priority.P1, i.getPriority());
        assertEquals(Status.ASSIGNED, i.getStatus()); assertNotNull(i.getTechnicianId());
    }
    @Test void t03_invalidTransitionBlocked() {
        Incident i = payment();
        assertThrows(InvalidStateTransitionException.class, () -> svc.close(admin, i.getId()));
    }
    @Test void t04_cannotResolveBeforeInvestigation() {
        Incident i = payment();
        assertThrows(InvalidStateTransitionException.class, () -> svc.resolve(tech(i), i.getId(), "x"));
    }
    @Test void t05_rejectedResolutionReopens() {
        Incident i = payment(); User t = tech(i);
        svc.acknowledge(t, i.getId()); svc.startInvestigation(t, i.getId()); svc.resolve(t, i.getId(), "fix");
        assertEquals(Status.IN_PROGRESS, svc.verify(emp, i.getId(), false).getStatus());
    }
    @Test void t06_auditTrailComplete() {
        Incident i = payment();
        List<String> actions = svc.auditLog(admin, i.getId()).stream().map(a -> a.action()).toList();
        assertEquals(List.of("INCIDENT_CREATED", "INCIDENT_CLASSIFIED", "PRIORITY_CALCULATED", "INCIDENT_ASSIGNED"), actions);
    }
    @Test void t07_exactDuplicateRejected() {
        payment();
        assertThrows(DuplicateIncidentException.class, this::payment);
    }

    // ---------- validation & security
    @Test void t08_invalidLogin() {
        assertThrows(AuthenticationException.class, () -> svc.login("eva@x.com", "wrong"));
        assertEquals(emp.getId(), svc.login("EVA@x.com", "EmpPass123").getId());
    }
    @Test void t09_employeeCannotManageUsers() {
        assertThrows(AuthorizationException.class, () -> svc.addUser(emp, Role.ADMIN, "X", "x@x.com", "Password123", null));
    }
    @Test void t10_employeeCannotViewOthersIncident() {
        Incident i = payment();
        assertThrows(AuthorizationException.class, () -> svc.viewIncident(emp2, i.getId()));
    }
    @Test void t11_wrongTechnicianCannotAcknowledge() {
        Incident i = payment(); User other = i.getTechnicianId().equals(t1.getId()) ? t2 : t1;
        assertThrows(AuthorizationException.class, () -> svc.acknowledge(other, i.getId()));
    }
    @Test void t12_unknownService() {
        assertThrows(ServiceNotFoundException.class, () -> report(emp, "Nope", 1, 1, 1, null));
    }
    @Test void t13_inputBoundaries() {
        for (int[] b : new int[][]{{0, 1, 1}, {4, 1, 1}, {1, 9, 1}, {1, 1, 0}})
            assertThrows(ValidationException.class, () -> report(emp, "Wiki", b[0], b[1], b[2], null));
        assertThrows(ValidationException.class, () -> svc.createIncident(emp, new IncidentRequest("  ", "d", "Wiki", 1, 1, 1)));
    }
    @Test void t14_weakPasswordRejected() {
        assertThrows(ValidationException.class, () -> svc.registerEmployee("A", "a1@x.com", "short"));
    }
    @Test void t15_missingSlaConfiguration() {
        IncidentService bare = new IncidentService(repo, clock, new SlaPolicy());
        assertThrows(MissingSlaConfigurationException.class, () -> bare.createIncident(emp, new IncidentRequest("t", "d", "Network", 3, 3, 1)));
    }
    @Test void t16_incidentNotFound() {
        assertThrows(InvalidIncidentException.class, () -> svc.viewIncident(admin, "INC9999"));
    }

    // ---------- strategies
    @Test void t17_priorityBoundaries() {
        PriorityStrategy s = new ScorePriorityStrategy();
        assertEquals(Priority.P1, s.calculate(3, 3, 3, 1));
        assertEquals(Priority.P2, s.calculate(3, 3, 1, 1));
        assertEquals(Priority.P3, s.calculate(2, 2, 1, 1));
        assertEquals(Priority.P4, s.calculate(1, 1, 3, 1));
        assertEquals(Priority.P2, s.calculate(3, 3, 1, 100));
    }
    @Test void t18_strategiesSwappable() {
        svc.setPriorityStrategy(admin, new WeightedPriorityStrategy());
        assertEquals(Priority.P1, payment().getPriority());
        assertEquals("weighted", svc.priorityStrategy().name());
    }
    @Test void t19_loadBasedSpreadsWork() {
        svc.setAssignmentStrategy(admin, new LoadBasedAssignment());
        assertNotEquals(network().getTechnicianId(), network().getTechnicianId());
    }
    @Test void t20_roundRobin() {
        svc.setAssignmentStrategy(admin, new RoundRobinAssignment());
        assertEquals(List.of(t1.getId(), t2.getId()), List.of(network().getTechnicianId(), network().getTechnicianId()));
    }
    @Test void t21_skillBasedPrefersExperience() { assertEquals(t1.getId(), network().getTechnicianId()); }
    @Test void t22_workloadExceeded() {
        network(); network(); network();                       // capacity 1 + 2
        Incident extra = network();
        assertEquals(Status.CLASSIFIED, extra.getStatus());
        assertThrows(WorkloadExceededException.class, () -> svc.assign(admin, extra.getId(), null));
    }
    @Test void t23_technicianUnavailable() {
        svc.setTechnicianAvailability(admin, t1.getId(), false); svc.setTechnicianAvailability(admin, t2.getId(), false);
        assertEquals(Status.CLASSIFIED, network().getStatus());
        assertThrows(TechnicianUnavailableException.class, () -> svc.assign(admin, "INC1001", t1.getId()));
    }

    // ---------- SLA & intelligence
    @Test void t24_slaWarningThenViolationOnce() {
        Incident i = payment();
        clock.advanceSeconds(270);
        assertEquals("RESPONSE_WARNING", svc.checkSla(admin).get(0).kind());
        clock.advanceMinutes(1);
        assertTrue(svc.checkSla(admin).stream().anyMatch(a -> a.kind().equals("RESPONSE_VIOLATED")));
        assertTrue(svc.checkSla(admin).stream().noneMatch(a -> a.kind().equals("RESPONSE_VIOLATED")));
        assertEquals("BREACHED", svc.viewIncident(admin, i.getId()).getSlaStatus());
    }
    @Test void t25_acknowledgedIncidentHasNoResponseViolation() {
        Incident i = payment(); svc.acknowledge(tech(i), i.getId());
        clock.advanceMinutes(10);
        assertTrue(svc.checkSla(admin).stream().noneMatch(a -> a.kind().equals("RESPONSE_VIOLATED")));
    }
    @Test void t26_p1EscalationLadder() {
        payment(); clock.advanceMinutes(6);
        assertTrue(svc.checkSla(admin).stream().anyMatch(a -> a.kind().equals("ESCALATED_TO_TEAM_LEAD")));
        clock.advanceMinutes(5);
        assertTrue(svc.checkSla(admin).stream().anyMatch(a -> a.kind().equals("ESCALATED_TO_SYSTEM_ADMINISTRATOR")));
    }
    @Test void t27_duplicateDetection() {
        Incident a = svc.createIncident(emp, new IncidentRequest("Payment application unavailable", "Users unable to complete transactions", "Payment", 3, 3, 10));
        Incident b = svc.createIncident(emp2, new IncidentRequest("Unable to make payment", "payment page errors", "Payment", 3, 3, 10));
        assertEquals(a.getId(), b.getRelated().get(0).incidentId());
    }
    @Test void t28_unrelatedNotFlagged() {
        payment();
        Incident c = svc.createIncident(emp, new IncidentRequest("Wiki typo", "spelling mistake on page", "Wiki", 1, 1, 1));
        assertTrue(c.getRelated().isEmpty());
    }
    @Test void t29_autoCategorisation() {
        Categorizer c = new Categorizer();
        assertEquals(Category.NETWORK, c.suggest("VPN keeps dropping, wifi slow"));
        assertEquals(Category.AUTHENTICATION, c.suggest("password locked out of SSO"));
        c.register(Category.SECURITY, "leak");
        assertEquals(Category.SECURITY, c.suggest("data leak"));
    }

    // ---------- search, reports, persistence, notifications
    @Test void t30_searchFiltersAndScoping() {
        Incident a = payment();
        svc.createIncident(emp2, new IncidentRequest("Wiki typo", "spelling", "Wiki", 1, 1, 1));
        assertEquals(2, svc.search(admin, IncidentQuery.all()).size());
        assertEquals(List.of(a.getId()), svc.search(admin, IncidentQuery.all().priority(Priority.P1)).stream().map(Incident::getId).toList());
        assertEquals(1, svc.search(admin, IncidentQuery.all().service("Wiki")).size());
        assertEquals(2, svc.search(admin, IncidentQuery.all().category(Category.APPLICATION)).size());
        assertEquals(1, svc.search(admin, IncidentQuery.all().keyword("spelling")).size());
        assertEquals(1, svc.search(admin, IncidentQuery.all().status(Status.ASSIGNED)).size());
        assertEquals(1, svc.search(admin, IncidentQuery.all().technician(a.getTechnicianId())).size());
        assertEquals(0, svc.search(admin, IncidentQuery.all().from(clock.get().toLocalDate().plusDays(1))).size());
        assertEquals(1, svc.search(emp, IncidentQuery.all()).size());     // employees only see their own
    }
    @Test void t31_reportsAndAuthorisation() {
        payment();
        Overview o = svc.overview(admin);
        assertEquals(new Overview(1, 1, 1, 0, 0), o);
        assertEquals(Map.of("Payment", 1), svc.serviceAnalysis(admin));
        assertThrows(AuthorizationException.class, () -> svc.overview(emp));
    }
    @Test void t32_filePersistenceSurvivesRestart() throws Exception {
        Path f = Files.createTempFile("iitsm", ".ser"); Files.delete(f);
        FileRepository fr = new FileRepository(f);
        IncidentService s = new IncidentService(fr, clock);
        User a = UserFactory.create(Role.ADMIN, "A101", "Admin", "a@x.com", "AdminPass1", null); fr.saveUser(a);
        s.addUser(a, Role.TECHNICIAN, "T", "t@x.com", "TechPass123", TechnicianProfile.of(3, 5, Category.APPLICATION));
        s.addService(a, new ITService("Payment", 3));
        User e = s.registerEmployee("E", "e@x.com", "EmpPass123");
        Incident i = s.createIncident(e, new IncidentRequest("Payment down", "fails", "Payment", 3, 3, 5)); s.addNote(e, i.getId(), "extra info");
        IncidentService reloaded = new IncidentService(new FileRepository(f), clock);
        Incident again = reloaded.viewIncident(reloaded.login("a@x.com", "AdminPass1"), i.getId());
        assertEquals(Priority.P1, again.getPriority()); assertEquals("extra info", again.getNotes().get(0).text());
        Files.deleteIfExists(f);
    }
    @Test void t33_notificationChannels() {
        Incident i = payment();
        assertEquals(Set.of("in_app", "email"), svc.notifications(tech(i)).stream().map(x -> x.channel()).collect(Collectors.toSet()));
        Incident low = svc.createIncident(emp2, new IncidentRequest("Wiki typo", "spelling", "Wiki", 1, 1, 1));
        assertEquals(Set.of("in_app"), svc.notifications(emp2).stream().map(x -> x.channel()).collect(Collectors.toSet()));
        assertEquals(Priority.P4, low.getPriority());
    }
    @Test void t34_feedbackOnlyAfterVerification() {
        Incident i = payment();
        assertThrows(InvalidStateTransitionException.class, () -> svc.giveFeedback(emp, i.getId(), 5, "x"));
    }
    @Test void t35_passwordsAreHashed() {
        assertTrue(emp.checkPassword("EmpPass123")); assertFalse(emp.checkPassword("empPass123"));
    }
}
