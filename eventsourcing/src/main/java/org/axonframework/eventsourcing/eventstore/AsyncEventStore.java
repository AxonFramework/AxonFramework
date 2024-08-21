package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * TODO fill in
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 0.1
 */ // TODO Rename to EventStore once fully integrated
public interface AsyncEventStore extends DescribableComponent {

    /**
     * Retrieves the {@link EventStoreTransaction transaction for appending events} for the given
     * {@code processingContext}. If no transaction is available, a new, empty transaction is created.
     *
     * @param processingContext The context for which to retrieve the {@link EventStoreTransaction}.
     * @return The {@link EventStoreTransaction}, existing or newly created, for the given {@code processingContext}.
     */
    EventStoreTransaction transaction(ProcessingContext processingContext, String context);

    /**
     * @param condition
     * @return
     */
    MessageStream<? extends EventMessage<?>> stream(StreamingCondition condition);

    // Token operations?
    CompletableFuture<TrackingToken> headToken();

    CompletableFuture<TrackingToken> tailToken();

    CompletableFuture<TrackingToken> tokenAt(Instant at);

    CompletableFuture<TrackingToken> tokenSince(Duration since);

    // TODO snapshots? Or separate SnapshotStore? Or layered on impl i.o. on the interface? Or a SnapshotStorageEngine that lives next to the EventStorageEngine, and are both used by this interface?
}
