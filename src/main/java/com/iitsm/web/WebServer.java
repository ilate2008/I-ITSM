package com.iitsm.web;

import com.iitsm.exception.*;
import com.iitsm.model.*;
import com.iitsm.notification.AuditRecord;
import com.iitsm.notification.Notification;
import com.iitsm.repository.IncidentQuery;
import com.iitsm.repository.Repository;
import com.iitsm.service.IncidentRequest;
import com.iitsm.service.IncidentService;
import com.iitsm.strategy.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** REST layer over IncidentService (JDK built-in HttpServer, no libraries). It holds no business logic: every rule lives in the service. */
public class WebServer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss");
    private final IncidentService svc;
    private final Repository repo;
    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000L;       // sessions expire after 30 idle minutes
    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOCKOUT_MS = 60 * 1000L;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private record Session(User user, long lastSeen) { }
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();   // token -> session
    private final Map<String, long[]> failedLogins = new ConcurrentHashMap<>(); // email -> {count, lockedUntil}
    private final HttpServer server;

    public WebServer(IncidentService svc, Repository repo, int port) throws IOException {
        this.svc = svc; this.repo = repo;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", ex -> send(ex, 200, "text/html; charset=utf-8", WebPage.HTML));
        server.createContext("/api/", this::api);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }

    // ---------------------------------------------------------------- routing
    private void api(HttpExchange ex) throws IOException {
        try {
            Map<String, String> p = params(ex);
            String path = ex.getRequestURI().getPath().substring(5);          // strip "/api/"
            if (path.equals("login")) { send(ex, 200, json(login(p))); return; }
            if (path.equals("register")) { svc.registerEmployee(p.get("name"), p.get("email"), p.get("password")); send(ex, 200, "{\"ok\":true}"); return; }
            String token = ex.getRequestHeaders().getFirst("X-Token");
            Session s = token == null ? null : sessions.get(token);
            if (s != null && System.currentTimeMillis() - s.lastSeen() > IDLE_TIMEOUT_MS) { sessions.remove(token); s = null; }
            if (s == null) throw new AuthenticationException("Session expired, please log in");
            sessions.put(token, new Session(s.user(), System.currentTimeMillis()));
            User u = s.user();
            send(ex, 200, json(route(path, u, p)));
        } catch (AuthenticationException e) { send(ex, 401, err(e));
        } catch (AuthorizationException e) { send(ex, 403, err(e));
        } catch (ItsmException e) { send(ex, 400, err(e));
        } catch (RuntimeException e) { send(ex, 500, err(new ItsmException("Server error: " + e.getMessage()))); }
    }

    private Object route(String path, User u, Map<String, String> p) {
        String id = p.get("id");
        switch (path) {
            case "logout": sessions.values().removeIf(s -> s.user().equals(u)); return Map.of("ok", true);
            case "stats": {                                   // chart data for the dashboard
                svc.overview(u);                              // enforces the REPORTS permission
                Map<String, Integer> byPriority = new LinkedHashMap<>(), byStatus = new LinkedHashMap<>(), byCategory = new TreeMap<>();
                for (Priority pr : Priority.values()) byPriority.put(pr.name(), 0);
                for (Status st : Status.values()) byStatus.put(st.name(), 0);
                for (Incident i : svc.search(u, IncidentQuery.all())) {
                    if (i.getPriority() != null) byPriority.merge(i.getPriority().name(), 1, Integer::sum);
                    byStatus.merge(i.getStatus().name(), 1, Integer::sum);
                    if (i.getCategory() != null) byCategory.merge(i.getCategory().name(), 1, Integer::sum);
                }
                return map("priority", byPriority, "status", byStatus, "category", byCategory);
            }
            case "services": return repo.listServices().stream().map(s -> map("name", s.getName(), "criticality", s.getCriticality())).toList();
            case "categories": return Arrays.stream(Category.values()).map(Enum::name).toList();
            case "suggest-category": return map("category", svc.categorizer().suggest(p.getOrDefault("text", "")).name());
            case "incidents": return svc.search(u, query(p)).stream().map(this::incident).toList();
            case "incident": return incident(svc.viewIncident(u, id));
            case "create": {
                Category c = blank(p.get("category")) ? null : Category.valueOf(p.get("category"));
                return incident(svc.createIncident(u, new IncidentRequest(p.get("title"), p.get("description"), p.get("service"),
                        num(p, "impact"), num(p, "urgency"), num(p, "users"), c)));
            }
            case "ack": return incident(svc.acknowledge(u, id));
            case "start": return incident(svc.startInvestigation(u, id));
            case "note": return incident(svc.addNote(u, id, p.get("text")));
            case "requestinfo": svc.requestInfo(u, id, p.get("text")); return incident(svc.viewIncident(u, id));
            case "resolve": return incident(svc.resolve(u, id, p.get("text")));
            case "verify": return incident(svc.verify(u, id, "true".equals(p.get("accepted"))));
            case "close": return incident(svc.close(u, id));
            case "feedback": svc.giveFeedback(u, id, num(p, "rating"), p.getOrDefault("text", "")); return incident(svc.viewIncident(u, id));
            case "assign": return incident(svc.assign(u, id, p.get("technician")));
            case "technicians": return technicians(u);
            case "overview": return svc.overview(u);
            case "workload": return svc.technicianWorkload(u);
            case "service-analysis": return svc.serviceAnalysis(u);
            case "audit": return (blank(id) ? svc.fullAudit(u) : svc.auditLog(u, id)).stream().map(this::audit).toList();
            case "notifications": return svc.notifications(u).stream().map(this::notification).toList();
            case "sla-check": return svc.checkSla(u).stream().map(a -> map("incident", a.incidentId(), "kind", a.kind())).toList();
            case "users": return repo.listUsers(null).stream().map(x -> map("id", x.getId(), "name", x.getName(), "email", x.getEmail(), "role", x.role().name())).toList();
            case "add-user": {
                Role r = Role.valueOf(p.get("role"));
                TechnicianProfile prof = r == Role.TECHNICIAN ? TechnicianProfile.of(num(p, "years"), num(p, "maxLoad"), skills(p.get("skills"))) : null;
                User n = svc.addUser(u, r, p.get("name"), p.get("email"), p.get("password"), prof);
                return map("id", n.getId());
            }
            case "availability": svc.setTechnicianAvailability(u, p.get("technician"), "true".equals(p.get("available"))); return Map.of("ok", true);
            case "add-service": svc.addService(u, new ITService(p.get("name"), num(p, "criticality"))); return Map.of("ok", true);
            case "config-sla": svc.configureSla(u, Priority.valueOf(p.get("priority")), num(p, "response"), num(p, "resolution")); return Map.of("ok", true);
            case "strategies": return map("assignment", svc.assignmentStrategy().name(), "priority", svc.priorityStrategy().name());
            case "set-strategy": {
                String n = p.get("name");
                if ("assignment".equals(p.get("type")))
                    svc.setAssignmentStrategy(u, switch (n) { case "load" -> new LoadBasedAssignment(); case "round_robin" -> new RoundRobinAssignment(); default -> new SkillBasedAssignment(); });
                else svc.setPriorityStrategy(u, "weighted".equals(n) ? new WeightedPriorityStrategy() : new ScorePriorityStrategy());
                return Map.of("ok", true);
            }
            default: throw new InvalidIncidentException("Unknown endpoint: " + path);
        }
    }

    private Map<String, Object> login(Map<String, String> p) {
        String key = String.valueOf(p.get("email")).trim().toLowerCase();
        long[] f = failedLogins.get(key);
        if (f != null && f[1] > System.currentTimeMillis()) throw new AuthenticationException("Too many failed attempts. Try again in a minute.");
        User u;
        try {
            u = svc.login(p.get("email"), p.get("password"));
        } catch (AuthenticationException e) {
            long[] cur = failedLogins.merge(key, new long[]{1, 0}, (a, b) -> new long[]{a[0] + 1, 0});
            if (cur[0] >= MAX_FAILED_LOGINS) failedLogins.put(key, new long[]{0, System.currentTimeMillis() + LOCKOUT_MS});
            throw e;
        }
        failedLogins.remove(key);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(u, System.currentTimeMillis()));
        return map("token", token, "id", u.getId(), "name", u.getName(), "role", u.role().name());
    }

    // ---------------------------------------------------------------- mapping domain -> JSON-able maps
    private Map<String, Object> incident(Incident i) {
        Map<String, Object> m = map("id", i.getId(), "title", i.getTitle(), "description", i.getDescription(), "service", i.getService(),
                "category", String.valueOf(i.getCategory()), "status", i.getStatus().name(), "priority", String.valueOf(i.getPriority()),
                "impact", i.getImpact(), "urgency", i.getUrgency(), "users", i.getAffectedUsers(), "reporter", name(i.getReporterId()),
                "technician", i.getTechnicianId() == null ? "" : name(i.getTechnicianId()), "technicianId", String.valueOf(i.getTechnicianId() == null ? "" : i.getTechnicianId()),
                "created", i.getCreatedAt().format(FMT), "responseDue", i.getResponseDue() == null ? "" : i.getResponseDue().format(FMT),
                "resolveDue", i.getResolveDue() == null ? "" : i.getResolveDue().format(FMT), "sla", i.getSlaStatus(),
                "resolution", i.getResolution() == null ? "" : i.getResolution(), "escalation", i.getEscalationLevel(),
                "rating", i.getFeedback() == null ? 0 : i.getFeedback().rating());
        m.put("notes", i.getNotes().stream().map(n -> map("at", n.at().format(FMT), "by", n.by(), "kind", n.kind(), "text", n.text())).toList());
        m.put("related", i.getRelated().stream().map(r -> map("id", r.incidentId(), "score", Math.round(r.score() * 100))).toList());
        return m;
    }
    private List<Map<String, Object>> technicians(User u) {
        Map<String, Integer> load = svc.technicianWorkload(u);
        List<Map<String, Object>> out = new ArrayList<>();
        for (User x : repo.listUsers(Role.TECHNICIAN)) {
            Technician t = (Technician) x;
            out.add(map("id", t.getId(), "name", t.getName(), "skills", t.getSkills().stream().map(Enum::name).sorted().toList(),
                    "years", t.getExperienceYears(), "maxLoad", t.getMaxLoad(), "available", t.isAvailable(), "load", load.getOrDefault(t.getId(), 0)));
        }
        return out;
    }
    private Map<String, Object> audit(AuditRecord a) {
        return map("time", a.timestamp().format(FMT), "user", a.user(), "action", a.action(), "incident", a.incidentId(),
                "from", a.oldStatus(), "to", a.newStatus(), "text", a.description());
    }
    private Map<String, Object> notification(Notification n) {
        return map("time", n.timestamp().format(FMT), "channel", n.channel(), "subject", n.subject(), "body", n.body());
    }
    private String name(String userId) { return repo.findUser(userId).map(User::getName).orElse(userId); }

    private IncidentQuery query(Map<String, String> p) {
        IncidentQuery q = IncidentQuery.all();
        if (!blank(p.get("id"))) q.id(p.get("id").trim());
        if (!blank(p.get("status"))) q.status(Status.valueOf(p.get("status")));
        if (!blank(p.get("priority"))) q.priority(Priority.valueOf(p.get("priority")));
        if (!blank(p.get("category"))) q.category(Category.valueOf(p.get("category")));
        if (!blank(p.get("service"))) q.service(p.get("service"));
        if (!blank(p.get("technician"))) q.technician(p.get("technician"));
        if (!blank(p.get("sla"))) q.slaStatus(p.get("sla"));
        if (!blank(p.get("keyword"))) q.keyword(p.get("keyword"));
        if (!blank(p.get("from"))) q.from(LocalDate.parse(p.get("from")));
        if (!blank(p.get("to"))) q.to(LocalDate.parse(p.get("to")));
        return q;
    }
    private Category[] skills(String csv) {
        if (blank(csv)) return new Category[0];
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Category::valueOf).toArray(Category[]::new);
    }

    // ---------------------------------------------------------------- helpers
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static int num(Map<String, String> p, String k) {
        try { return Integer.parseInt(p.getOrDefault(k, "").trim()); }
        catch (NumberFormatException e) { throw new ValidationException("'" + k + "' must be a number"); }
    }
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    private static String err(Exception e) { return json(map("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); }

    private static Map<String, String> params(HttpExchange ex) throws IOException {
        String raw = ex.getRequestURI().getRawQuery();
        byte[] raw8 = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (raw8.length > MAX_BODY_BYTES) throw new ValidationException("Request too large");
        String body = new String(raw8, StandardCharsets.UTF_8);
        Map<String, String> m = new HashMap<>();
        for (String part : ((raw == null ? "" : raw) + "&" + body).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }
    private static void send(HttpExchange ex, int code, String json) throws IOException { send(ex, code, "application/json; charset=utf-8", json); }
    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (type.startsWith("text/html"))
            ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    /** Minimal JSON writer for maps, lists, strings, numbers, booleans and records. */
    static String json(Object o) {
        if (o == null) return "null";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?, ?> m) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            m.forEach((k, v) -> j.add(json(String.valueOf(k)) + ":" + json(v)));
            return j.toString();
        }
        if (o instanceof Collection<?> c) { StringJoiner j = new StringJoiner(",", "[", "]"); c.forEach(x -> j.add(json(x))); return j.toString(); }
        if (o instanceof com.iitsm.service.Overview v)
            return json(map("total", v.total(), "open", v.open(), "critical", v.critical(), "resolved", v.resolved(), "slaViolations", v.slaViolations()));
        StringBuilder sb = new StringBuilder("\"");
        for (char c : o.toString().toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\""); case '\\' -> sb.append("\\\\"); case '\n' -> sb.append("\\n"); case '\r' -> sb.append("\\r"); case '\t' -> sb.append("\\t");
                default -> { if (c < 32) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
