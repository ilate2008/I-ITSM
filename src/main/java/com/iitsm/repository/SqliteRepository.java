package com.iitsm.repository;

import com.iitsm.exception.PersistenceException;
import com.iitsm.model.*;
import com.iitsm.notification.AuditRecord;
import com.iitsm.notification.Notification;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository pattern, relational implementation (SQLite over JDBC).
 *
 * <p>Design: a write-through cache. All reads are served from the in-memory model inherited from
 * {@link InMemoryRepository}; every save is first written to the database inside a transaction. On start-up the
 * whole database is loaded back into that model. The rest of the application only sees the {@link Repository}
 * interface, so this class replaced {@link FileRepository} without a single change to the business logic.
 */
public class SqliteRepository extends InMemoryRepository implements AutoCloseable {

    /** The schema is also shipped as docs/schema.sql for the report. */
    public static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS users (
          id            TEXT PRIMARY KEY,
          role          TEXT NOT NULL CHECK (role IN ('EMPLOYEE','TECHNICIAN','ADMIN')),
          name          TEXT NOT NULL,
          email         TEXT NOT NULL UNIQUE,
          password_hash TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS technician_profiles (
          user_id          TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
          experience_years INTEGER NOT NULL CHECK (experience_years >= 0),
          max_load         INTEGER NOT NULL CHECK (max_load > 0),
          available        INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS technician_skills (
          user_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          category TEXT NOT NULL,
          PRIMARY KEY (user_id, category)
        );
        CREATE TABLE IF NOT EXISTS technician_service_experience (
          user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          service_name TEXT NOT NULL,
          years        INTEGER NOT NULL,
          PRIMARY KEY (user_id, service_name)
        );
        CREATE TABLE IF NOT EXISTS it_services (
          name        TEXT PRIMARY KEY,
          criticality INTEGER NOT NULL CHECK (criticality BETWEEN 1 AND 3),
          description TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE IF NOT EXISTS sla_policies (
          priority           TEXT PRIMARY KEY CHECK (priority IN ('P1','P2','P3','P4')),
          response_minutes   INTEGER NOT NULL CHECK (response_minutes > 0),
          resolution_minutes INTEGER NOT NULL CHECK (resolution_minutes >= response_minutes)
        );
        CREATE TABLE IF NOT EXISTS service_sla (
          service_name       TEXT NOT NULL REFERENCES it_services(name) ON DELETE CASCADE,
          priority           TEXT NOT NULL CHECK (priority IN ('P1','P2','P3','P4')),
          response_minutes   INTEGER NOT NULL,
          resolution_minutes INTEGER NOT NULL,
          PRIMARY KEY (service_name, priority)
        );
        CREATE TABLE IF NOT EXISTS incidents (
          id               TEXT PRIMARY KEY,
          reporter_id      TEXT NOT NULL REFERENCES users(id),
          title            TEXT NOT NULL,
          description      TEXT NOT NULL,
          service_name     TEXT NOT NULL REFERENCES it_services(name),
          impact           INTEGER NOT NULL CHECK (impact BETWEEN 1 AND 3),
          urgency          INTEGER NOT NULL CHECK (urgency BETWEEN 1 AND 3),
          affected_users   INTEGER NOT NULL,
          created_at       TEXT NOT NULL,
          category         TEXT,
          status           TEXT NOT NULL CHECK (status IN ('NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','VERIFIED','CLOSED')),
          priority         TEXT CHECK (priority IN ('P1','P2','P3','P4')),
          technician_id    TEXT REFERENCES users(id),
          response_due     TEXT,
          resolve_due      TEXT,
          acknowledged_at  TEXT,
          resolved_at      TEXT,
          closed_at        TEXT,
          resolution       TEXT,
          feedback_rating  INTEGER CHECK (feedback_rating BETWEEN 1 AND 5),
          feedback_comment TEXT,
          escalation_level INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_incidents_status  ON incidents(status);
        CREATE INDEX IF NOT EXISTS idx_incidents_tech    ON incidents(technician_id);
        CREATE INDEX IF NOT EXISTS idx_incidents_service ON incidents(service_name);
        CREATE INDEX IF NOT EXISTS idx_incidents_prio    ON incidents(priority);
        CREATE TABLE IF NOT EXISTS incident_notes (
          id          INTEGER PRIMARY KEY AUTOINCREMENT,
          incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
          noted_at    TEXT NOT NULL,
          author      TEXT NOT NULL,
          kind        TEXT NOT NULL,
          body        TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS incident_sla_flags (
          incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
          flag        TEXT NOT NULL,
          PRIMARY KEY (incident_id, flag)
        );
        CREATE TABLE IF NOT EXISTS related_incidents (
          incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
          related_id  TEXT NOT NULL,
          score       REAL NOT NULL,
          position    INTEGER NOT NULL,
          PRIMARY KEY (incident_id, related_id)
        );
        CREATE TABLE IF NOT EXISTS audit_log (
          id          INTEGER PRIMARY KEY AUTOINCREMENT,
          at          TEXT NOT NULL,
          actor       TEXT NOT NULL,
          action      TEXT NOT NULL,
          incident_id TEXT NOT NULL,
          old_status  TEXT NOT NULL,
          new_status  TEXT NOT NULL,
          description TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_audit_incident ON audit_log(incident_id);
        CREATE TABLE IF NOT EXISTS notifications (
          id      INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          channel TEXT NOT NULL,
          subject TEXT NOT NULL,
          body    TEXT NOT NULL,
          at      TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id)
        """;

    /** True when the sqlite-jdbc driver jar is on the classpath (e.g. dropped into the lib/ folder). */
    public static boolean driverAvailable() {
        try { Class.forName("org.sqlite.JDBC"); return true; } catch (ClassNotFoundException e) { return false; }
    }

    @FunctionalInterface private interface Work { void run() throws SQLException; }

    private final Connection db;

    /** @param path database file, e.g. "iitsm.db", or ":memory:" for a throw-away database */
    public SqliteRepository(String path) {
        try {
            db = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = db.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                for (String sql : SCHEMA.split(";")) if (!sql.isBlank()) st.execute(sql);
            }
            load();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot open database '" + path + "' (is the sqlite-jdbc driver on the classpath?): " + e.getMessage(), e);
        }
        if (loadDefaultSla().isEmpty()) saveDefaultSla(SlaPolicy.defaults());
    }

    @Override public synchronized void close() {
        try { db.close(); } catch (SQLException e) { throw new PersistenceException("Could not close database", e); }
    }

    // ------------------------------------------------------------------ write-through saves
    @Override public synchronized void saveUser(User u) {
        tx(() -> {
            update("INSERT INTO users(id,role,name,email,password_hash) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                    + "role=excluded.role,name=excluded.name,email=excluded.email,password_hash=excluded.password_hash",
                    u.getId(), u.role().name(), u.getName(), u.getEmail(), u.getPasswordHash());
            if (u instanceof Technician t) {
                update("INSERT INTO technician_profiles(user_id,experience_years,max_load,available) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET "
                        + "experience_years=excluded.experience_years,max_load=excluded.max_load,available=excluded.available",
                        t.getId(), t.getExperienceYears(), t.getMaxLoad(), t.isAvailable() ? 1 : 0);
                update("DELETE FROM technician_skills WHERE user_id=?", t.getId());
                for (Category c : t.getSkills()) update("INSERT INTO technician_skills(user_id,category) VALUES(?,?)", t.getId(), c.name());
                update("DELETE FROM technician_service_experience WHERE user_id=?", t.getId());
                for (Map.Entry<String, Integer> e : t.getServiceExperience().entrySet())
                    update("INSERT INTO technician_service_experience(user_id,service_name,years) VALUES(?,?,?)", t.getId(), e.getKey(), e.getValue());
            }
        });
        super.saveUser(u);
    }

    @Override public synchronized void saveService(ITService s) {
        tx(() -> {
            update("INSERT INTO it_services(name,criticality,description) VALUES(?,?,?) ON CONFLICT(name) DO UPDATE SET "
                    + "criticality=excluded.criticality,description=excluded.description",
                    s.getName(), s.getCriticality(), s.getDescription() == null ? "" : s.getDescription());
            update("DELETE FROM service_sla WHERE service_name=?", s.getName());
            if (s.getSla() != null) {
                for (Map.Entry<Priority, int[]> e : s.getSla().asMap().entrySet())
                    update("INSERT INTO service_sla(service_name,priority,response_minutes,resolution_minutes) VALUES(?,?,?,?)",
                            s.getName(), e.getKey().name(), e.getValue()[0], e.getValue()[1]);
            }
        });
        super.saveService(s);
    }

    @Override public synchronized void saveIncident(Incident i) {
        tx(() -> {
            update("INSERT INTO incidents(id,reporter_id,title,description,service_name,impact,urgency,affected_users,created_at,category,status,priority,"
                    + "technician_id,response_due,resolve_due,acknowledged_at,resolved_at,closed_at,resolution,feedback_rating,feedback_comment,escalation_level) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                    + "category=excluded.category,status=excluded.status,priority=excluded.priority,technician_id=excluded.technician_id,"
                    + "response_due=excluded.response_due,resolve_due=excluded.resolve_due,acknowledged_at=excluded.acknowledged_at,"
                    + "resolved_at=excluded.resolved_at,closed_at=excluded.closed_at,resolution=excluded.resolution,"
                    + "feedback_rating=excluded.feedback_rating,feedback_comment=excluded.feedback_comment,escalation_level=excluded.escalation_level",
                    i.getId(), i.getReporterId(), i.getTitle(), i.getDescription(), i.getService(), i.getImpact(), i.getUrgency(), i.getAffectedUsers(),
                    str(i.getCreatedAt()), i.getCategory() == null ? null : i.getCategory().name(), i.getStatus().name(),
                    i.getPriority() == null ? null : i.getPriority().name(), i.getTechnicianId(), str(i.getResponseDue()), str(i.getResolveDue()),
                    str(i.getAcknowledgedAt()), str(i.getResolvedAt()), str(i.getClosedAt()), i.getResolution(),
                    i.getFeedback() == null ? null : i.getFeedback().rating(), i.getFeedback() == null ? null : i.getFeedback().comment(),
                    i.getEscalationLevel());
            update("DELETE FROM incident_notes WHERE incident_id=?", i.getId());
            for (Note n : i.getNotes())
                update("INSERT INTO incident_notes(incident_id,noted_at,author,kind,body) VALUES(?,?,?,?,?)", i.getId(), str(n.at()), n.by(), n.kind(), n.text());
            update("DELETE FROM incident_sla_flags WHERE incident_id=?", i.getId());
            for (SlaFlag f : SlaFlag.values()) if (i.hasFlag(f)) update("INSERT INTO incident_sla_flags(incident_id,flag) VALUES(?,?)", i.getId(), f.name());
            update("DELETE FROM related_incidents WHERE incident_id=?", i.getId());
            int pos = 0;
            for (RelatedIncident r : i.getRelated())
                update("INSERT INTO related_incidents(incident_id,related_id,score,position) VALUES(?,?,?,?)", i.getId(), r.incidentId(), r.score(), pos++);
        });
        super.saveIncident(i);
    }

    @Override public synchronized void addAudit(AuditRecord r) {
        tx(() -> update("INSERT INTO audit_log(at,actor,action,incident_id,old_status,new_status,description) VALUES(?,?,?,?,?,?,?)",
                str(r.timestamp()), r.user(), r.action(), r.incidentId(), r.oldStatus(), r.newStatus(), r.description()));
        super.addAudit(r);
    }

    @Override public synchronized void addNotification(Notification n) {
        tx(() -> update("INSERT INTO notifications(user_id,channel,subject,body,at) VALUES(?,?,?,?,?)",
                n.userId(), n.channel(), n.subject(), n.body(), str(n.timestamp())));
        super.addNotification(n);
    }

    @Override public synchronized void saveDefaultSla(SlaPolicy p) {
        tx(() -> {
            update("DELETE FROM sla_policies");
            for (Map.Entry<Priority, int[]> e : p.asMap().entrySet())
                update("INSERT INTO sla_policies(priority,response_minutes,resolution_minutes) VALUES(?,?,?)", e.getKey().name(), e.getValue()[0], e.getValue()[1]);
        });
    }

    @Override public synchronized Optional<SlaPolicy> loadDefaultSla() {
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT priority,response_minutes,resolution_minutes FROM sla_policies")) {
            SlaPolicy p = new SlaPolicy();
            boolean any = false;
            while (rs.next()) { p.setTarget(Priority.valueOf(rs.getString(1)), rs.getInt(2), rs.getInt(3)); any = true; }
            return any ? Optional.of(p) : Optional.empty();
        } catch (SQLException e) { throw new PersistenceException("Could not read SLA policy", e); }
    }

    // ------------------------------------------------------------------ load everything at start-up
    private void load() throws SQLException {
        Map<String, Set<Category>> skills = new HashMap<>();
        for (String[] r : rows("SELECT user_id,category FROM technician_skills", 2))
            skills.computeIfAbsent(r[0], k -> EnumSet.noneOf(Category.class)).add(Category.valueOf(r[1]));
        Map<String, Map<String, Integer>> exp = new HashMap<>();
        for (String[] r : rows("SELECT user_id,service_name,years FROM technician_service_experience", 3))
            exp.computeIfAbsent(r[0], k -> new HashMap<>()).put(r[1], Integer.parseInt(r[2]));

        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT u.id,u.role,u.name,u.email,u.password_hash,p.experience_years,p.max_load,p.available "
                + "FROM users u LEFT JOIN technician_profiles p ON p.user_id=u.id ORDER BY u.id")) {
            while (rs.next()) {
                Role role = Role.valueOf(rs.getString(2));
                String id = rs.getString(1);
                TechnicianProfile profile = role != Role.TECHNICIAN ? null : new TechnicianProfile(
                        skills.getOrDefault(id, EnumSet.noneOf(Category.class)), rs.getInt(6), rs.getInt(7), exp.getOrDefault(id, Map.of()));
                User u = UserFactory.restore(role, id, rs.getString(3), rs.getString(4), rs.getString(5), profile);
                if (u instanceof Technician t) t.setAvailable(rs.getInt(8) == 1);
                data.users.put(id, u);
            }
        }

        Map<String, SlaPolicy> serviceSla = new HashMap<>();
        for (String[] r : rows("SELECT service_name,priority,response_minutes,resolution_minutes FROM service_sla", 4))
            serviceSla.computeIfAbsent(r[0], k -> new SlaPolicy()).setTarget(Priority.valueOf(r[1]), Integer.parseInt(r[2]), Integer.parseInt(r[3]));
        for (String[] r : rows("SELECT name,criticality,description FROM it_services ORDER BY name", 3))
            data.services.put(r[0], new ITService(r[0], Integer.parseInt(r[1]), r[2], serviceSla.get(r[0])));

        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM incidents ORDER BY id")) {
            while (rs.next()) {
                Incident i = new Incident(rs.getString("id"), rs.getString("reporter_id"), rs.getString("title"), rs.getString("description"),
                        rs.getString("service_name"), rs.getInt("impact"), rs.getInt("urgency"), rs.getInt("affected_users"), time(rs.getString("created_at")));
                if (rs.getString("category") != null) i.setCategory(Category.valueOf(rs.getString("category")));
                if (rs.getString("priority") != null) i.setPriority(Priority.valueOf(rs.getString("priority")), time(rs.getString("response_due")), time(rs.getString("resolve_due")));
                if (rs.getString("technician_id") != null) i.assignTo(rs.getString("technician_id"));
                i.setResolution(rs.getString("resolution"));
                int rating = rs.getInt("feedback_rating");
                if (!rs.wasNull()) i.setFeedback(new Feedback(rating, rs.getString("feedback_comment")));
                i.setEscalationLevel(rs.getInt("escalation_level"));
                i.restoreLifecycle(Status.valueOf(rs.getString("status")), time(rs.getString("acknowledged_at")), time(rs.getString("resolved_at")), time(rs.getString("closed_at")));
                data.incidents.put(i.getId(), i);
            }
        }
        for (String[] r : rows("SELECT incident_id,noted_at,author,kind,body FROM incident_notes ORDER BY id", 5))
            data.incidents.get(r[0]).addNote(new Note(time(r[1]), r[2], r[3], r[4]));
        for (String[] r : rows("SELECT incident_id,flag FROM incident_sla_flags", 2))
            data.incidents.get(r[0]).addSlaFlag(SlaFlag.valueOf(r[1]));
        Map<String, List<RelatedIncident>> related = new HashMap<>();
        for (String[] r : rows("SELECT incident_id,related_id,score FROM related_incidents ORDER BY incident_id,position", 3))
            related.computeIfAbsent(r[0], k -> new ArrayList<>()).add(new RelatedIncident(r[1], Double.parseDouble(r[2])));
        related.forEach((id, list) -> data.incidents.get(id).setRelated(list));

        for (String[] r : rows("SELECT at,actor,action,incident_id,old_status,new_status,description FROM audit_log ORDER BY id", 7))
            data.audit.add(new AuditRecord(time(r[0]), r[1], r[2], r[3], r[4], r[5], r[6]));
        for (String[] r : rows("SELECT user_id,channel,subject,body,at FROM notifications ORDER BY id", 5))
            data.notifications.add(new Notification(r[0], r[1], r[2], r[3], time(r[4])));

        int max = 0;
        for (String id : data.incidents.keySet()) {
            try { max = Math.max(max, Integer.parseInt(id.substring(3)) - 1000); } catch (NumberFormatException ignored) { }
        }
        data.incidentCounter = max;
    }

    // ------------------------------------------------------------------ JDBC helpers
    private List<String[]> rows(String sql, int cols) throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] row = new String[cols];
                for (int c = 0; c < cols; c++) row[c] = rs.getString(c + 1);
                out.add(row);
            }
        }
        return out;
    }

    private void update(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    /** Runs the statements as one atomic transaction; any failure rolls everything back. */
    private void tx(Work work) {
        try {
            db.setAutoCommit(false);
            try { work.run(); db.commit(); }
            catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
            finally { db.setAutoCommit(true); }
        } catch (SQLException e) {
            throw new PersistenceException("Database error: " + e.getMessage(), e);
        }
    }

    private static String str(LocalDateTime t) { return t == null ? null : t.toString(); }
    private static LocalDateTime time(String s) { return s == null ? null : LocalDateTime.parse(s); }
}
