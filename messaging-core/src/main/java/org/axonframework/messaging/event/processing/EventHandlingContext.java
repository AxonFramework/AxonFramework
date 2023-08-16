package org.axonframework.messaging.event.processing;

import org.axonframework.messaging.MessageHandlingContext;
import org.axonframework.messaging.event.EventMessage;

public interface EventHandlingContext extends MessageHandlingContext<EventMessage<?>> {

    int sequencingIdentifier();

    Segment segment();

    TrackingToken token();

    TrackingToken retryToken();
}
