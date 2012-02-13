package org.axonframework.test;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedEntity;

/**
 *
 */
public class MyEntity extends AbstractAnnotatedEntity {

    private Integer lastNumber;

    @EventHandler
    public void handleMyEvent(MyEvent event) {
        lastNumber = event.getSomeValue();
    }

    public Integer getLastNumber() {
        return lastNumber;
    }
}
