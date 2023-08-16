package org.axonframework.messaging.event.processing;

import org.axonframework.messaging.ProcessingContext;
import org.axonframework.messaging.event.EventMessage;
import reactor.core.CompletableFuture.Mono;

import java.util.Optional;

/**
 * Formerly known as the Event Handler Invoker
 */
public interface ProcessingGroup {

    String name();

    Mono<Void> handle(EventMessage event, ProcessingContext processingContext);

    default Optional<TrackingToken> segmentClaimed(Segment segment) {
        return Optional.empty();
    }

    default void segmentReleased(Segment segment) {
    }
}
