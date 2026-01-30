/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * An {@link EventStore} implementation recording all the events that are
 * {@link #publish(ProcessingContext, List) published}.
 * <p>
 * The recorded events can then be used to assert expectations with test cases.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingEventStore extends RecordingEventSink implements EventStore {

    private static final Logger logger = LoggerFactory.getLogger(RecordingEventStore.class);

    private final EventStore eventStore;

    /**
     * Creates a new {@link RecordingEventStore} that will record all events published to the given {@code delegate}.
     *
     * @param delegate The {@link EventStore} to which events will be published.
     */
    public RecordingEventStore(@Nonnull EventStore delegate) {
        super(Objects.requireNonNull(delegate, "The delegate EventStore may not be null"));
        this.eventStore = delegate;
    }

    @Override
    public EventStoreTransaction transaction(@NotNull ProcessingContext processingContext) {
        logger.debug("transaction() called on thread {}", Thread.currentThread().getName());
        return eventStore.transaction(processingContext);
    }

    @Override
    public void describeTo(@NotNull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    @Override
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
                                            @Nullable ProcessingContext context) {
        logger.debug("open() called with condition: {} on thread {}",
                     condition,
                     Thread.currentThread().getName());
        return eventStore.open(condition, context);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return eventStore.firstToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return eventStore.latestToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return eventStore.tokenAt(at, context);
    }

    @Override
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
        return eventStore.subscribe(eventsBatchConsumer);
    }
}
