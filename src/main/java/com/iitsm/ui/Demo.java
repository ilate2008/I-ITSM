package com.iitsm.ui;

import com.iitsm.model.*;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.InMemoryRepository;
import com.iitsm.service.*;
import com.iitsm.strategy.LoadBasedAssignment;

/** Scripted run of the three workflows required by the project brief. */
public class Demo {
    private static void head(String t) { System.out.println("\n======== " + t + " ========"); }

    public static void main(String[] args) {
        InMemoryRepository repo = new InMemoryRepository();
        ManualClock clock = new ManualClock();
        IncidentService svc = new IncidentService(repo, clock);
        User admin = SeedData.seed(svc, repo);
        User eva = svc.login("eva@corp.com", "EmpPass123");
        User ravi = svc.registerEmployee("Ravi Employee", "ravi@corp.com", "EmpPass123");

        head("Workflow 1: report -> classify -> prioritise -> assign -> notify");
        Incident i1 = svc.createIncident(eva, new IncidentRequest("Payment application unavailable",
                "Users are unable to complete transactions", "Payment", 3, 3, 250));
        System.out.println(i1);
        System.out.println("Technician channels notified: " + svc.notifications(repo.findUser(i1.getTechnicianId()).get())
                .stream().map(n -> n.channel()).toList());

        head("Workflow 2: investigate -> SLA monitored -> resolved -> user notified");
        User tech = repo.findUser(i1.getTechnicianId()).get();
        clock.advanceMinutes(3);
        svc.acknowledge(tech, i1.getId()); svc.startInvestigation(tech, i1.getId());
        clock.advanceMinutes(20);
        System.out.println("SLA alerts: " + svc.checkSla(admin));
        svc.resolve(tech, i1.getId(), "Restarted payment gateway and cleared stuck queue");
        svc.verify(eva, i1.getId(), true); svc.close(eva, i1.getId()); svc.giveFeedback(eva, i1.getId(), 5, "Fast fix");
        svc.auditLog(admin, i1.getId()).forEach(a -> System.out.println("  " + a));
        System.out.println("Reporter notifications: " + svc.notifications(eva).size());

        head("Workflow 3: duplicates, correlation and escalation");
        svc.setAssignmentStrategy(admin, new LoadBasedAssignment());
        Incident i2 = svc.createIncident(ravi, new IncidentRequest("Payment transaction failing", "Transactions fail at checkout", "Payment", 3, 3, 120));
        Incident i3 = svc.createIncident(eva, new IncidentRequest("Unable to make payment", "Payment page errors out", "Payment", 3, 2, 80));
        System.out.println(i2.getId() + " related: " + i2.getRelated());
        System.out.println(i3.getId() + " related: " + i3.getRelated());
        clock.advanceMinutes(6); System.out.println("SLA check 1: " + svc.checkSla(admin));
        clock.advanceMinutes(6); System.out.println("SLA check 2: " + svc.checkSla(admin));

        head("Reports");
        System.out.println(svc.overview(admin));
        System.out.println("Workload: " + svc.technicianWorkload(admin));
        System.out.println("By service: " + svc.serviceAnalysis(admin));
        System.out.println("Breached: " + svc.search(admin, IncidentQuery.all().slaStatus("BREACHED")).stream().map(Incident::getId).toList());
    }
}
