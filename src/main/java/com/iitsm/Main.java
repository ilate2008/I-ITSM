package com.iitsm;

import com.iitsm.model.User;
import com.iitsm.repository.FileRepository;
import com.iitsm.repository.InMemoryRepository;
import com.iitsm.repository.SqliteRepository;
import com.iitsm.notification.AlertSinkObserver;
import com.iitsm.notification.EmailObserver;
import com.iitsm.notification.SmtpEmailSender;
import com.iitsm.notification.HttpWebhookClient;
import com.iitsm.notification.WebhookAlertAdapter;
import com.iitsm.service.IncidentService;
import com.iitsm.service.SlaMonitor;
import com.iitsm.ui.ConsoleApp;
import com.iitsm.ui.Demo;
import com.iitsm.ui.SeedData;
import com.iitsm.web.WebServer;
import java.time.LocalDateTime;

/** Entry point.  `Main` = console app,  `Main web [port]` = web app,  `Main demo` = scripted workflows. */
public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("demo")) { Demo.main(args); return; }
        boolean web = args.length > 0 && args[0].equals("web");
        // Persistence: SQLite when the driver jar is in lib/, otherwise the serialised-file repository (no extra downloads needed)
        InMemoryRepository repo;
        if (SqliteRepository.driverAvailable()) {
            repo = new SqliteRepository(System.getProperty("iitsm.db", "iitsm.db"));
            System.out.println("Storage: SQLite database (iitsm.db)");
        } else {
            repo = new FileRepository(java.nio.file.Path.of("iitsm-data.ser"));
            System.out.println("Storage: file iitsm-data.ser (put sqlite-jdbc-3.36.0.3.jar in the lib/ folder to use the SQLite database)");
        }
        IncidentService svc = new IncidentService(repo, LocalDateTime::now);
        // Optional real integration: set IITSM_WEBHOOK_URL to a Slack/Discord/Teams-style incoming webhook
        String webhook = System.getenv("IITSM_WEBHOOK_URL");
        if (webhook != null && !webhook.isBlank()) {
            svc.subscribe(new AlertSinkObserver(new WebhookAlertAdapter(new HttpWebhookClient(webhook))));
            System.out.println("External alerts enabled: critical events are posted to the configured webhook.");
        }
        // Optional real e-mail (free with a Gmail app password): set IITSM_SMTP_USER and IITSM_SMTP_PASS
        String smtpUser = System.getenv("IITSM_SMTP_USER"), smtpPass = System.getenv("IITSM_SMTP_PASS");
        if (smtpUser != null && !smtpUser.isBlank() && smtpPass != null && !smtpPass.isBlank()) {
            String host = System.getenv().getOrDefault("IITSM_SMTP_HOST", "smtp.gmail.com");
            int smtpPort = Integer.parseInt(System.getenv().getOrDefault("IITSM_SMTP_PORT", "465"));
            svc.subscribe(new EmailObserver(repo, new SmtpEmailSender(host, smtpPort, smtpUser, smtpPass, System.getenv("IITSM_MAIL_TO"))));
            System.out.println("Real e-mail enabled via " + host + (System.getenv("IITSM_MAIL_TO") != null ? " (all mail redirected to IITSM_MAIL_TO)" : ""));
        }
        if (repo.isEmpty()) {
            SeedData.seed(svc, repo);
            System.out.println("First start: sample data created.\n  admin:      admin@corp.com / AdminPass1\n"
                    + "  technician: tara@corp.com, tej@corp.com, tia@corp.com / TechPass123\n  employee:   eva@corp.com / EmpPass123\n");
        }
        try (SlaMonitor monitor = new SlaMonitor(svc, 30)) {
            if (web) {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                try {
                    new WebServer(svc, repo, port).start();
                } catch (java.io.IOException e) {
                    throw new com.iitsm.exception.PersistenceException("Cannot start web server on port " + port + ": " + e.getMessage(), e);
                }
                System.out.println("I-ITSM web app running at http://localhost:" + port + "  (Ctrl+C to stop)");
                try { Thread.currentThread().join(); } catch (InterruptedException ignored) { }
            } else {
                new ConsoleApp(svc, repo).run();
            }
        }
    }
}
