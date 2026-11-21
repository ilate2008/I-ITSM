package com.iitsm.notification;

/** Observer pattern contract. */
public interface EventObserver {
    void update(Event event);
}
