package com.iitsm.ui;

import com.iitsm.model.*;
import com.iitsm.repository.Repository;
import com.iitsm.service.IncidentService;
import java.util.Map;

/** Initial users and services so the application is usable on first start. */
public final class SeedData {
    private SeedData() { }

    public static User seed(IncidentService svc, Repository repo) {
        User admin = UserFactory.create(Role.ADMIN, "A101", "Asha Admin", "admin@corp.com", "AdminPass1", null);
        repo.saveUser(admin);
        svc.addUser(admin, Role.TECHNICIAN, "Tara", "tara@corp.com", "TechPass123",
                TechnicianProfile.of(6, 5, Map.of("Payment", 3), Category.APPLICATION, Category.DATABASE, Category.AUTHENTICATION));
        svc.addUser(admin, Role.TECHNICIAN, "Tej", "tej@corp.com", "TechPass123",
                TechnicianProfile.of(4, 5, Category.NETWORK, Category.CLOUD, Category.STORAGE));
        svc.addUser(admin, Role.TECHNICIAN, "Tia", "tia@corp.com", "TechPass123",
                TechnicianProfile.of(2, 5, Category.APPLICATION, Category.SOFTWARE, Category.HARDWARE, Category.SECURITY));
        for (Object[] s : new Object[][]{{"Payment", 3}, {"Network", 2}, {"Authentication", 2}, {"Database", 3}, {"Cloud Storage", 1}, {"Email", 1}}) {
            svc.addService(admin, new ITService((String) s[0], (Integer) s[1]));
        }
        svc.registerEmployee("Eva Employee", "eva@corp.com", "EmpPass123");
        return admin;
    }
}
