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

import org.jspecify.annotations.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.interception.CorrelationDataInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Configuration-level {@link EventStore} decorator that wraps each {@link EventStoreTransaction} in a
 * {@link IsolatingEventStoreTransaction}. The scoped transaction adds the test isolation tag to
 * {@link EventStoreTransaction#source(org.axonframework.eventsourcing.eventstore.SourcingCondition) sourcing}
 * criteria, ensuring that each test only sources its own events.
 * <p>
 * All other {@code EventStore} methods are delegated unchanged.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingEventStore implements EventStore {

    private final EventStore delegate;

    /**
     * Constructs a new {@code IsolatingEventStore} wrapping the given {@code delegate}.
     *
     * @param delegate The {@link EventStore} to delegate to.
     */
    IsolatingEventStore(EventStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventStore may not be null");
    }

    @Override
    public EventStoreTransaction transaction(ProcessingContext processingContext) {
        return new IsolatingEventStoreTransaction(
                delegate.transaction(processingContext),
                processingContext
        );
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           List<? extends EventMessage> events) {
        var testId = extractTestId(context);
        if (testId == null) {
            return delegate.publish(context, events);
        }
        var stamped = events.stream()
                .map(e -> e.andMetadata(Map.of(TEST_ID_METADATA_KEY, testId)))
                .toList();
        return delegate.publish(context, stamped);
    }

    @Nullable
    private static String extractTestId(@Nullable ProcessingContext context) {
        if (context == null || !context.containsResource(CorrelationDataInterceptor.CORRELATION_DATA)) {
            return null;
        }
        var correlationData = context.getResource(CorrelationDataInterceptor.CORRELATION_DATA);
        return correlationData != null ? correlationData.get(TEST_ID_METADATA_KEY) : null;
    }

    @Override
    public MessageStream<EventMessage> open(StreamingCondition condition,
                                            @Nullable ProcessingContext context) {
        return delegate.open(condition, context);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return delegate.firstToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return delegate.latestToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(Instant at, @Nullable ProcessingContext context) {
        return delegate.tokenAt(at, context);
    }

    @Override
    public Registration subscribe(
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
        return delegate.subscribe(eventsBatchConsumer);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
