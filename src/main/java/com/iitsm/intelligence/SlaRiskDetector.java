package com.iitsm.intelligence;

import java.time.Duration;
import java.time.LocalDateTime;

/** Intelligent feature E: an incident is "at risk" once a configurable share of its SLA window has elapsed. */
public class SlaRiskDetector {
    private final double warnRatio;
    public SlaRiskDetector(double warnRatio) { this.warnRatio = warnRatio; }
    public SlaRiskDetector() { this(0.8); }

    public boolean atRisk(LocalDateTime start, LocalDateTime due, LocalDateTime now) {
        double total = Duration.between(start, due).toMillis();
        return total <= 0 || Duration.between(start, now).toMillis() / total >= warnRatio;
    }
}
