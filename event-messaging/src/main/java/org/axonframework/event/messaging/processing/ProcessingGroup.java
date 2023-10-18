package org.axonframework.event.messaging.processing;

import org.axonframework.event.messaging.EventMessage;
import org.axonframework.messaging.ProcessingContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Formerly known as the Event Handler Invoker
 */
public interface ProcessingGroup {

    String name();

    CompletableFuture<Void> handle(EventMessage event, ProcessingContext processingContext);

    default Optional<TrackingToken> segmentClaimed(Segment segment) {
        return Optional.empty();
    }

    default void segmentReleased(Segment segment) {
    }
}
