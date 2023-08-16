package org.axonframework.messaging.event;

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SimpleMessage;

import java.time.Instant;

public class SimpleEventMessage<T> extends SimpleMessage<T> implements EventMessage<T> {

    private final Instant timestamp = EventMessage.clock.instant();

    public SimpleEventMessage(T payload) {
        this(QualifiedName.fromClass(payload.getClass()), payload);
    }

    public SimpleEventMessage(QualifiedName name, T payload) {
        super(name, payload, MetaData.empty());
    }

    public static EventMessage<?> asEventMessage(Object event) {
        if (event instanceof EventMessage<?> eventMessage) {
            return eventMessage;
        }
        return new SimpleEventMessage<>(event);
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

}
