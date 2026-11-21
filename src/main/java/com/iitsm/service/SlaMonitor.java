package com.iitsm.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Background SLA monitoring: calls IncidentService.checkSla() every interval. */
public class SlaMonitor implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sla-monitor"); t.setDaemon(true); return t; });

    public SlaMonitor(IncidentService service, long intervalSeconds) {
        scheduler.scheduleAtFixedRate(() -> {
            try { service.checkSla(null); } catch (RuntimeException e) { System.err.println("[sla-monitor] " + e.getMessage()); }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override public void close() { scheduler.shutdownNow(); }
}
