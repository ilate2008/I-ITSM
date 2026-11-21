package com.iitsm.service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/** Controllable clock for demos and tests. */
public class ManualClock implements Supplier<LocalDateTime> {
    private LocalDateTime now;
    public ManualClock(LocalDateTime start) { now = start; }
    public ManualClock() { this(LocalDateTime.of(2026, 11, 16, 10, 0)); }
    @Override public LocalDateTime get() { return now; }
    public void advanceMinutes(long m) { now = now.plusMinutes(m); }
    public void advanceSeconds(long s) { now = now.plusSeconds(s); }
}
