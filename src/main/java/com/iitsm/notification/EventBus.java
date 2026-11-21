package com.iitsm.notification;

import java.util.ArrayList;
import java.util.List;

/** Subject of the Observer pattern. A failing observer never breaks the business workflow. */
public class EventBus {
    private final List<EventObserver> observers = new ArrayList<>();

    public void subscribe(EventObserver o) { observers.add(o); }

    public void publish(Event e) {
        for (EventObserver o : observers) {
            try {
                o.update(e);
            } catch (RuntimeException ex) {
                System.err.println("[warn] observer " + o.getClass().getSimpleName() + " failed: " + ex.getMessage());
            }
        }
    }
}
