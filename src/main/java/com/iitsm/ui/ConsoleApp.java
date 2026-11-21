package com.iitsm.ui;

import com.iitsm.exception.ItsmException;
import com.iitsm.model.*;
import com.iitsm.notification.Notification;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.Repository;
import com.iitsm.service.*;
import com.iitsm.strategy.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/** Console front-end with a separate menu for each role. */
public class ConsoleApp {
    private final IncidentService svc;
    private final Repository repo;
    private final Scanner in = new Scanner(System.in);

    public ConsoleApp(IncidentService svc, Repository repo) { this.svc = svc; this.repo = repo; }

    // ---------------------------------------------------------------- input helpers
    private String ask(String prompt) { System.out.print(prompt + ": "); return in.hasNextLine() ? in.nextLine().trim() : ""; }
    private int askInt(String prompt) {
        try { return Integer.parseInt(ask(prompt)); } catch (NumberFormatException e) { throw new com.iitsm.exception.ValidationException("Please enter a number"); }
    }
    private <E extends Enum<E>> E askEnum(String prompt, Class<E> type) {
        String s = ask(prompt + " " + List.of(type.getEnumConstants()) + " (blank = any)");
        if (s.isEmpty()) return null;
        try { return Enum.valueOf(type, s.toUpperCase()); } catch (IllegalArgumentException e) { throw new com.iitsm.exception.ValidationException("Invalid value: " + s); }
    }
    private String blankToNull(String s) { return s.isEmpty() ? null : s; }

    private void print(Incident i) {
        System.out.println("------------------------------------------------------------");
        System.out.println(i.getId() + "  " + i.getTitle());
        System.out.println("Status: " + i.getStatus() + " | Priority: " + i.getPriority() + " | Category: " + i.getCategory()
                + " | Service: " + i.getService() + " | SLA: " + i.getSlaStatus());
        System.out.println("Reporter: " + i.getReporterId() + " | Technician: " + (i.getTechnicianId() == null ? "-" : i.getTechnicianId())
                + " | Created: " + i.getCreatedAt() + " | Response due: " + i.getResponseDue() + " | Resolve due: " + i.getResolveDue());
        System.out.println("Description: " + i.getDescription());
        if (!i.getRelated().isEmpty()) System.out.println("Possibly related: " + i.getRelated());
        i.getNotes().forEach(n -> System.out.println("  [" + n.kind() + " by " + n.by() + " @ " + n.at() + "] " + n.text()));
        if (i.getResolution() != null) System.out.println("Resolution: " + i.getResolution());
        if (i.getFeedback() != null) System.out.println("Feedback: " + i.getFeedback().rating() + "/5 " + i.getFeedback().comment());
    }
    private void list(List<Incident> l) { if (l.isEmpty()) System.out.println("(none)"); l.forEach(i -> System.out.println(i)); }

    // ---------------------------------------------------------------- main loop
    public void run() {
        System.out.println("=== Intelligent IT Service Management & Incident Resolution System ===");
        while (true) {
            System.out.println("\n1) Login  2) Register as employee  0) Exit");
            String c = ask("Choice");
            try {
                switch (c) {
                    case "1" -> session(svc.login(ask("Email"), ask("Password")));
                    case "2" -> { Employee e = svc.registerEmployee(ask("Name"), ask("Email"), ask("Password (min 8 chars)")); System.out.println("Registered. Your id is " + e.getId()); }
                    case "0" -> { return; }
                    default -> System.out.println("Unknown option");
                }
            } catch (ItsmException e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private void session(User u) {
        System.out.println("\nWelcome " + u.getName() + " (" + u.role() + ")");
        boolean run = true;
        while (run) {
            try {
                run = switch (u.role()) {
                    case EMPLOYEE -> employeeMenu(u);
                    case TECHNICIAN -> technicianMenu(u);
                    case ADMIN -> adminMenu(u);
                };
            } catch (ItsmException e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private void showNotifications(User u) {
        List<Notification> l = svc.notifications(u);
        if (l.isEmpty()) System.out.println("No notifications");
        l.stream().skip(Math.max(0, l.size() - 15)).forEach(n -> System.out.println("[" + n.channel() + " " + n.timestamp() + "] " + n.subject() + " - " + n.body().replace("\n", " ")));
    }

    // ---------------------------------------------------------------- employee
    private boolean employeeMenu(User u) {
        System.out.println("\n[Employee] 1 Report incident | 2 My incidents | 3 Incident details & history | 4 Add information | "
                + "5 Verify/reject resolution | 6 Close incident | 7 Give feedback | 8 Notifications | 0 Logout");
        switch (ask("Choice")) {
            case "1" -> {
                String title = ask("Title"), desc = ask("Description");
                System.out.println("Services: " + repo.listServices().stream().map(ITService::getName).collect(Collectors.joining(", ")));
                String svcName = ask("Affected service");
                int impact = askInt("Impact (1 low - 3 high)"), urgency = askInt("Urgency (1 low - 3 high)"), users = askInt("Approx. users affected");
                Incident i = svc.createIncident(u, new IncidentRequest(title, desc, svcName, impact, urgency, users));
                print(i);
            }
            case "2" -> list(svc.search(u, IncidentQuery.all()));
            case "3" -> { String id = ask("Incident id"); print(svc.viewIncident(u, id)); svc.auditLog(u, id).forEach(a -> System.out.println("  " + a)); }
            case "4" -> svc.addNote(u, ask("Incident id"), ask("Additional information"));
            case "5" -> { String id = ask("Incident id"); svc.verify(u, id, ask("Accept the resolution? (y/n)").equalsIgnoreCase("y")); System.out.println("Done"); }
            case "6" -> { svc.close(u, ask("Incident id")); System.out.println("Closed"); }
            case "7" -> { svc.giveFeedback(u, ask("Incident id"), askInt("Rating 1-5"), ask("Comment")); System.out.println("Thank you"); }
            case "8" -> showNotifications(u);
            case "0" -> { return false; }
            default -> System.out.println("Unknown option");
        }
        return true;
    }

    // ---------------------------------------------------------------- technician
    private boolean technicianMenu(User u) {
        System.out.println("\n[Technician] 1 My incidents | 2 Acknowledge | 3 Start investigation | 4 Add note | "
                + "5 Request info from reporter | 6 Resolve | 7 My workload | 8 Notifications | 0 Logout");
        switch (ask("Choice")) {
            case "1" -> svc.search(u, IncidentQuery.all()).forEach(this::print);
            case "2" -> { svc.acknowledge(u, ask("Incident id")); System.out.println("Acknowledged"); }
            case "3" -> { svc.startInvestigation(u, ask("Incident id")); System.out.println("Investigation started"); }
            case "4" -> svc.addNote(u, ask("Incident id"), ask("Investigation note"));
            case "5" -> svc.requestInfo(u, ask("Incident id"), ask("Question for reporter"));
            case "6" -> { svc.resolve(u, ask("Incident id"), ask("Resolution details")); System.out.println("Marked RESOLVED"); }
            case "7" -> System.out.println("Active incidents: " + svc.technicianWorkload(u).get(u.getId()) + " / max " + ((Technician) u).getMaxLoad());
            case "8" -> showNotifications(u);
            case "0" -> { return false; }
            default -> System.out.println("Unknown option");
        }
        return true;
    }

    // ---------------------------------------------------------------- administrator
    private boolean adminMenu(User u) {
        System.out.println("\n[Admin] 1 Search/monitor incidents | 2 Assign incident | 3 Manage users | 4 Manage services | 5 Configure SLA | "
                + "6 Strategies | 7 Reports | 8 Audit log | 9 Run SLA check | 10 Incident details | 11 Notifications | 0 Logout");
        switch (ask("Choice")) {
            case "1" -> {
                IncidentQuery q = IncidentQuery.all().id(blankToNull(ask("Incident id (blank = any)")));
                q.status(); Status st = askEnum("Status", Status.class); if (st != null) q.status(st);
                q.priority(askEnum("Priority", Priority.class)).category(askEnum("Category", Category.class))
                        .service(blankToNull(ask("Service (blank = any)"))).technician(blankToNull(ask("Technician id (blank = any)")))
                        .slaStatus(blankToNull(ask("SLA status ON_TRACK/AT_RISK/BREACHED (blank = any)"))).keyword(blankToNull(ask("Keyword (blank = any)")));
                String from = ask("From date yyyy-MM-dd (blank = any)"), to = ask("To date yyyy-MM-dd (blank = any)");
                try {
                    if (!from.isEmpty()) q.from(LocalDate.parse(from));
                    if (!to.isEmpty()) q.to(LocalDate.parse(to));
                } catch (java.time.format.DateTimeParseException e) { throw new com.iitsm.exception.ValidationException("Dates must look like 2026-11-16"); }
                list(svc.search(u, q));
            }
            case "2" -> {
                String id = ask("Incident id"), tech = ask("Technician id (blank = use current strategy)");
                print(svc.assign(u, id, blankToNull(tech)));
            }
            case "3" -> manageUsers(u);
            case "4" -> {
                if (ask("1 Add/update service  2 List services").equals("1"))
                    svc.addService(u, new ITService(ask("Service name"), askInt("Business criticality 1-3"), ask("Description"), null));
                else repo.listServices().forEach(s -> System.out.println(s.getName() + " (criticality " + s.getCriticality() + ")"));
            }
            case "5" -> {
                Priority p = askEnum("Priority", Priority.class);
                svc.configureSla(u, p, askInt("Response target (minutes)"), askInt("Resolution target (minutes)"));
                System.out.println("SLA updated");
            }
            case "6" -> {
                System.out.println("Current assignment: " + svc.assignmentStrategy().name() + ", priority: " + svc.priorityStrategy().name());
                switch (ask("1 skill  2 load  3 round-robin  4 score-priority  5 weighted-priority")) {
                    case "1" -> svc.setAssignmentStrategy(u, new SkillBasedAssignment());
                    case "2" -> svc.setAssignmentStrategy(u, new LoadBasedAssignment());
                    case "3" -> svc.setAssignmentStrategy(u, new RoundRobinAssignment());
                    case "4" -> svc.setPriorityStrategy(u, new ScorePriorityStrategy());
                    case "5" -> svc.setPriorityStrategy(u, new WeightedPriorityStrategy());
                    default -> System.out.println("Unchanged");
                }
            }
            case "7" -> {
                System.out.println(svc.overview(u));
                System.out.println("Technician workload: " + svc.technicianWorkload(u));
                System.out.println("Incidents per service: " + svc.serviceAnalysis(u));
            }
            case "8" -> svc.fullAudit(u).forEach(a -> System.out.println(a));
            case "9" -> { List<SlaAlert> a = svc.checkSla(u); System.out.println(a.isEmpty() ? "No new SLA alerts" : a.toString()); }
            case "10" -> { String id = ask("Incident id"); print(svc.viewIncident(u, id)); svc.auditLog(u, id).forEach(a -> System.out.println("  " + a)); }
            case "11" -> showNotifications(u);
            case "0" -> { return false; }
            default -> System.out.println("Unknown option");
        }
        return true;
    }

    private void manageUsers(User admin) {
        switch (ask("1 List users  2 Add employee  3 Add technician  4 Set technician availability")) {
            case "1" -> repo.listUsers(null).forEach(x -> System.out.println(x + " " + x.getEmail()));
            case "2" -> System.out.println("Created " + svc.addUser(admin, Role.EMPLOYEE, ask("Name"), ask("Email"), ask("Password"), null));
            case "3" -> {
                String name = ask("Name"), email = ask("Email"), pw = ask("Password");
                Category[] skills = java.util.Arrays.stream(ask("Skills (comma separated, e.g. NETWORK,CLOUD)").split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(s -> Category.valueOf(s.toUpperCase())).toArray(Category[]::new);
                System.out.println("Created " + svc.addUser(admin, Role.TECHNICIAN, name, email, pw,
                        TechnicianProfile.of(askInt("Years of experience"), askInt("Max concurrent incidents"), Map.of(), skills)));
            }
            case "4" -> { svc.setTechnicianAvailability(admin, ask("Technician id"), ask("Available? (y/n)").equalsIgnoreCase("y")); System.out.println("Updated"); }
            default -> System.out.println("Unchanged");
        }
    }
}
