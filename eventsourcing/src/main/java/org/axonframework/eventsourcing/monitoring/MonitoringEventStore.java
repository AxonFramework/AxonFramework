/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.monitoring;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.monitoring.MonitoringEventSink;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class MonitoringEventStore implements EventStore {

    private final EventStore delegate;
    private final MessageMonitor<Message> messageMonitor;
    private final MonitoringEventSink sink;

    public MonitoringEventStore(@Nonnull EventStore delegate, @Nonnull MessageMonitor<Message> messageMonitor) {
        this.delegate = requireNonNull(delegate, "event store delegate may not be null");
        this.messageMonitor = requireNonNull(messageMonitor, "message monitor may not be null");
        // re-use the monitoring behavior of MonitoringEventSink
        this.sink = new MonitoringEventSink(delegate, messageMonitor);
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context, @Nonnull List<EventMessage> events) {
        return sink.publish(context, events);
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        return delegate.transaction(processingContext);
    }

    @Override
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
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
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return delegate.tokenAt(at, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }

}
