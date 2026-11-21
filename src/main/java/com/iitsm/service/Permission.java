package com.iitsm.service;

import com.iitsm.model.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Role-based access control table. */
public enum Permission {
    CREATE_INCIDENT, ADD_INFO, VERIFY, CLOSE, FEEDBACK, SEARCH,
    ACKNOWLEDGE, INVESTIGATE, RESOLVE, ADD_NOTE, REQUEST_INFO,
    MANAGE_USERS, MANAGE_SERVICES, CONFIGURE_SLA, CONFIGURE_STRATEGY, ASSIGN, REPORTS, AUDIT_ALL, MONITOR_SLA;

    private static final Map<Role, Set<Permission>> GRANTS = new EnumMap<>(Role.class);
    static {
        GRANTS.put(Role.EMPLOYEE, EnumSet.of(CREATE_INCIDENT, ADD_INFO, VERIFY, CLOSE, FEEDBACK, SEARCH));
        GRANTS.put(Role.TECHNICIAN, EnumSet.of(ACKNOWLEDGE, INVESTIGATE, RESOLVE, ADD_NOTE, REQUEST_INFO, SEARCH));
        GRANTS.put(Role.ADMIN, EnumSet.of(CREATE_INCIDENT, VERIFY, CLOSE, SEARCH, ADD_NOTE, MANAGE_USERS, MANAGE_SERVICES,
                CONFIGURE_SLA, CONFIGURE_STRATEGY, ASSIGN, REPORTS, AUDIT_ALL, MONITOR_SLA));
    }

    public static boolean isGranted(Role role, Permission p) { return GRANTS.getOrDefault(role, Set.of()).contains(p); }
}
