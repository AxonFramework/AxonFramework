package org.axonframework.saga.annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Allard Buijze
 */
public class InvocationLogger {

    private List<Object> events = new ArrayList<Object>();

    public void logEvent(Object event) {
        this.events.add(event);
    }

    public void reset() {
        events.clear();
    }

    public List<Object> getEvents() {
        return events;
    }
}
