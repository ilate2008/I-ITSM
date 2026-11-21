package com.iitsm;

import static org.junit.jupiter.api.Assertions.*;

import com.iitsm.model.*;
import com.iitsm.notification.AlertSinkObserver;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.SqliteRepository;
import com.iitsm.service.IncidentRequest;
import com.iitsm.service.IncidentService;
import com.iitsm.service.ManualClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for the relational (SQLite) repository: data must survive a restart unchanged. */
class SqliteRepositoryTest {
    Path file;
    ManualClock clock = new ManualClock();

    @BeforeEach void setUp() throws Exception { file = Files.createTempFile("iitsm", ".db"); }
    @AfterEach void tearDown() throws Exception { Files.deleteIfExists(file); }

    private IncidentService open(SqliteRepository repo) { return new IncidentService(repo, clock); }

    private User seed(SqliteRepository repo, IncidentService s) {
        User a = UserFactory.create(Role.ADMIN, "A101", "Admin", "a@x.com", "AdminPass1", null);
        repo.saveUser(a);
        s.addUser(a, Role.TECHNICIAN, "Tess", "t@x.com", "TechPass123", TechnicianProfile.of(4, 3, java.util.Map.of("Payment", 2), Category.APPLICATION));
        s.addService(a, new ITService("Payment", 3));
        return a;
    }

    @Test void t36_fullIncidentSurvivesRestart() {
        String id;
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            IncidentService s = open(repo);
            User a = seed(repo, s);
            User e = s.registerEmployee("Eva", "e@x.com", "EmpPass123");
            Incident i = s.createIncident(e, new IncidentRequest("Payment app down", "transactions fail", "Payment", 3, 3, 10));
            id = i.getId();
            User t = repo.findUser(i.getTechnicianId()).get();
            s.acknowledge(t, id); s.startInvestigation(t, id); s.addNote(t, id, "checking gateway");
            s.resolve(t, id, "restarted gateway"); s.verify(e, id, true); s.close(e, id); s.giveFeedback(e, id, 5, "fast");
        }
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            IncidentService s = open(repo);
            User a = s.login("a@x.com", "AdminPass1");
            Incident i = s.viewIncident(a, id);
            assertEquals(Status.CLOSED, i.getStatus());
            assertEquals(Priority.P1, i.getPriority());
            assertEquals("restarted gateway", i.getResolution());
            assertEquals(5, i.getFeedback().rating());
            assertNotNull(i.getClosedAt());
            assertTrue(i.getNotes().stream().anyMatch(n -> n.text().equals("checking gateway")));
            assertFalse(s.auditLog(a, id).isEmpty());
            Technician t = (Technician) repo.findUser(i.getTechnicianId()).get();
            assertTrue(t.hasSkill(Category.APPLICATION));
            assertEquals(2, t.experienceWith("Payment"));
        }
    }

    @Test void t37_configuredSlaPolicyPersists() {
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            IncidentService s = open(repo);
            User a = seed(repo, s);
            s.configureSla(a, Priority.P1, 7, 45);
        }
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            SlaPolicy p = repo.loadDefaultSla().orElseThrow();
            assertEquals(7, p.targetsFor(Priority.P1).response().toMinutes());
            assertEquals(45, p.targetsFor(Priority.P1).resolution().toMinutes());
        }
    }

    @Test void t38_incidentIdsContinueAfterRestartAndSearchWorks() {
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            IncidentService s = open(repo);
            seed(repo, s);
            User e = s.registerEmployee("Eva", "e@x.com", "EmpPass123");
            s.createIncident(e, new IncidentRequest("Payment app down", "transactions fail", "Payment", 3, 3, 10));
        }
        try (SqliteRepository repo = new SqliteRepository(file.toString())) {
            IncidentService s = open(repo);
            User e = s.login("e@x.com", "EmpPass123");
            Incident second = s.createIncident(e, new IncidentRequest("Wallet page blank", "nothing renders", "Payment", 1, 1, 1));
            assertEquals("INC1002", second.getId());
            assertEquals(2, repo.findIncidents(IncidentQuery.all()).size());
        }
    }

    @Test void t39_externalAlertAdapterForwardsOnlyImportantEvents() {
        try (SqliteRepository repo = new SqliteRepository(":memory:")) {
            IncidentService s = open(repo);
            seed(repo, s);
            List<String> sent = new ArrayList<>();
            s.subscribe(new AlertSinkObserver((subject, msg) -> sent.add(subject)));
            User e = s.registerEmployee("Eva", "e@x.com", "EmpPass123");
            s.createIncident(e, new IncidentRequest("Payment app down", "transactions fail", "Payment", 3, 3, 10));
            int afterCritical = sent.size();
            assertTrue(afterCritical > 0);
            s.createIncident(e, new IncidentRequest("Typo on wiki", "spelling mistake", "Payment", 1, 1, 1));
            assertEquals(afterCritical, sent.size());
        }
    }
}
