package com.iitsm.model;

import com.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.exception.ValidationException;
import java.io.Serializable;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Maps each priority to response/resolution targets (in minutes). */
public class SlaPolicy implements Serializable {
    private final Map<Priority, int[]> targets = new EnumMap<>(Priority.class);

    public static SlaPolicy defaults() {
        SlaPolicy p = new SlaPolicy();
        p.setTarget(Priority.P1, 5, 30);
        p.setTarget(Priority.P2, 15, 120);
        p.setTarget(Priority.P3, 60, 480);
        p.setTarget(Priority.P4, 240, 1440);
        return p;
    }

    public void setTarget(Priority priority, int responseMinutes, int resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes < responseMinutes) {
            throw new ValidationException("Invalid SLA targets: need 0 < response <= resolution");
        }
        targets.put(priority, new int[]{responseMinutes, resolutionMinutes});
    }

    public boolean covers(Priority priority) { return targets.containsKey(priority); }

    public SlaTarget targetsFor(Priority priority) {
        int[] t = targets.get(priority);
        if (t == null) throw new MissingSlaConfigurationException("No SLA configured for " + priority);
        return new SlaTarget(Duration.ofMinutes(t[0]), Duration.ofMinutes(t[1]));
    }

    public Map<Priority, int[]> asMap() { return new EnumMap<>(targets); }
}
