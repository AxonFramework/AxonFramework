package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */ // TODO Rename to EmbeddedEventStore once fully integrated
// TODO Discuss whether this operation needs "the smarts" as present in the EmbeddedEventStore to combine multiple readers by maintaining a windowed cache.
// Essentially these smarts have moved to the PooledStreamingEventProcessor already.. Although, the caching is an additional optimization on top of this.
// Which we aren't using for Axon Server at all right now.
public class AsyncEmbeddedEventStore implements AsyncEventStore {

    private final ProcessingContext.ResourceKey<EventStoreTransaction> eventStoreTransactionKey =
            ProcessingContext.ResourceKey.create("StoreTransaction");

    private final AsyncEventStorageEngine storageEngine;
    private final Clock clock;

    public AsyncEmbeddedEventStore(AsyncEventStorageEngine storageEngine, Clock clock) {
        this.storageEngine = storageEngine;
        this.clock = clock;
    }

    @Override
    public EventStoreTransaction transaction(ProcessingContext processingContext, String context) {
        return processingContext.computeResourceIfAbsent(
                eventStoreTransactionKey,
                () -> new QueueingEventStoreTransaction(processingContext, storageEngine)
        );
    }

    @Override
    public MessageStream<? extends EventMessage<?>> stream(StreamingCondition condition) {
        return storageEngine.stream(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return storageEngine.headToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return storageEngine.tailToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(Instant at) {
        return storageEngine.tokenAt(at);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenSince(Duration since) {
        return tokenAt(clock.instant().minus(since));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStorageEngine", storageEngine);
        descriptor.describeProperty("clock", clock);
    }
}
