package org.axonframework.event.messaging.processing;

import org.axonframework.event.messaging.EventMessage;
import org.axonframework.messaging.MessageHandlingContext;

public interface EventHandlingContext extends MessageHandlingContext<EventMessage> {

    int sequencingIdentifier();

    Segment segment();

    TrackingToken token();

    TrackingToken retryToken();
}
