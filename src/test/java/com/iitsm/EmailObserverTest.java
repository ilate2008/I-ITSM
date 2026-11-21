package com.iitsm;

import static org.junit.jupiter.api.Assertions.*;

import com.iitsm.model.*;
import com.iitsm.notification.EmailObserver;
import com.iitsm.repository.InMemoryRepository;
import com.iitsm.service.IncidentRequest;
import com.iitsm.service.IncidentService;
import com.iitsm.service.ManualClock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailObserverTest {
    @Test void t40_emailsSentOnlyForImportantIncidents() {
        InMemoryRepository repo = new InMemoryRepository();
        IncidentService svc = new IncidentService(repo, new ManualClock());
        User admin = UserFactory.create(Role.ADMIN, "A101", "Admin", "a@x.com", "AdminPass1", null);
        repo.saveUser(admin);
        svc.addUser(admin, Role.TECHNICIAN, "T", "t@x.com", "TechPass123", TechnicianProfile.of(3, 5, Category.APPLICATION));
        svc.addService(admin, new ITService("Payment", 3));
        List<String> mails = new ArrayList<>();
        svc.subscribe(new EmailObserver(repo, (to, subject, body) -> mails.add(to + "|" + subject)));
        User e = svc.registerEmployee("Eva", "eva@x.com", "EmpPass123");
        svc.createIncident(e, new IncidentRequest("Payment app down", "transactions fail", "Payment", 3, 3, 10));
        assertFalse(mails.isEmpty());
        assertTrue(mails.stream().anyMatch(m -> m.startsWith("eva@x.com|")));
        int before = mails.size();
        svc.createIncident(e, new IncidentRequest("Typo in wiki", "spelling mistake", "Payment", 1, 1, 1));
        assertEquals(before, mails.size());
    }
}
