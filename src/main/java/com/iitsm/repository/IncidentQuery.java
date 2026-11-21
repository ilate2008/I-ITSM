package com.iitsm.repository;

import com.iitsm.model.Category;
import com.iitsm.model.Incident;
import com.iitsm.model.Priority;
import com.iitsm.model.Status;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Search criteria object (fluent builder). Null fields are ignored. */
public class IncidentQuery {
    private String id, service, technicianId, reporterId, slaStatus, keyword;
    private Set<Status> statuses;
    private Priority priority;
    private Category category;
    private LocalDate from, to;

    public static IncidentQuery all() { return new IncidentQuery(); }
    public IncidentQuery id(String v) { id = v; return this; }
    public IncidentQuery status(Status... v) { statuses = v.length == 0 ? null : Set.of(v); return this; }
    public IncidentQuery statuses(Collection<Status> v) { statuses = Set.copyOf(v); return this; }
    public IncidentQuery priority(Priority v) { priority = v; return this; }
    public IncidentQuery category(Category v) { category = v; return this; }
    public IncidentQuery service(String v) { service = v; return this; }
    public IncidentQuery technician(String v) { technicianId = v; return this; }
    public IncidentQuery reporter(String v) { reporterId = v; return this; }
    public IncidentQuery slaStatus(String v) { slaStatus = v; return this; }
    public IncidentQuery keyword(String v) { keyword = v; return this; }
    public IncidentQuery from(LocalDate v) { from = v; return this; }
    public IncidentQuery to(LocalDate v) { to = v; return this; }

    public boolean matches(Incident i) {
        if (id != null && !i.getId().equalsIgnoreCase(id)) return false;
        if (statuses != null && !statuses.contains(i.getStatus())) return false;
        if (priority != null && i.getPriority() != priority) return false;
        if (category != null && i.getCategory() != category) return false;
        if (service != null && !i.getService().equalsIgnoreCase(service)) return false;
        if (technicianId != null && !technicianId.equals(i.getTechnicianId())) return false;
        if (reporterId != null && !reporterId.equals(i.getReporterId())) return false;
        if (slaStatus != null && !i.getSlaStatus().equalsIgnoreCase(slaStatus)) return false;
        if (from != null && i.getCreatedAt().toLocalDate().isBefore(from)) return false;
        if (to != null && i.getCreatedAt().toLocalDate().isAfter(to)) return false;
        if (keyword != null && !(i.getTitle() + " " + i.getDescription()).toLowerCase().contains(keyword.toLowerCase())) return false;
        return true;
    }

    /** Returns a copy restricted to the given reporter / technician (used for role-based scoping). */
    public IncidentQuery scopedTo(String reporter, String technician) {
        IncidentQuery q = new IncidentQuery();
        q.id = id; q.service = service; q.slaStatus = slaStatus; q.keyword = keyword; q.statuses = statuses;
        q.priority = priority; q.category = category; q.from = from; q.to = to;
        q.reporterId = reporter != null ? reporter : reporterId;
        q.technicianId = technician != null ? technician : technicianId;
        return q;
    }

    public List<Incident> filter(Collection<Incident> all) { return all.stream().filter(this::matches).toList(); }
}
