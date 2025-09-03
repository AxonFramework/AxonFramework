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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link EventSink} implementation recording all the events that are
 * {@link #publish(ProcessingContext, List) published}.
 * <p>
 * The recorded events can then be used to assert expectations with test cases.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingEventSink implements EventSink, DescribableComponent {

    protected static final Logger logger = LoggerFactory.getLogger(RecordingEventSink.class);

    private static final UUID uuid = UUID.randomUUID();
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    public final String instanceId = INSTANCE_COUNTER.incrementAndGet() + "@" + uuid;

    private final List<EventMessage> recorded = new ArrayList<>();

    protected final EventSink delegate;

    /**
     * Creates a new {@link RecordingEventSink} that will record all events published to the given {@code delegate}.
     * @param delegate The {@link EventSink} to which events will be published.
     */
    public RecordingEventSink(@Nonnull EventSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventSink may not be null");
        logger.info("RecordingEventSink[" + instanceId + "] CREATED: delegate=" + delegate.getClass());
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        for (EventMessage event : events) {
            logger.info("RecordingEventSink[" + instanceId + "] RECORDING: " + event.identifier());
        }
        synchronized (recorded) {
            recorded.addAll(events);
            logger.info("RecordingEventSink[" + instanceId + "] RECORDED: size=" + recorded.size() + ", total events=" + recorded);
        }
        CompletableFuture<Void> result = delegate.publish(context, events)
                .thenRun(() -> logger.info("RecordingEventSink[" + instanceId + "] PUBLISHED: size=" + recorded.size() + ", total events=" + recorded));
        return result;
    }

    public List<EventMessage> recorded() {
        synchronized (recorded) {
            logger.info("RecordingEventSink[" + instanceId + "] READ: size=" + recorded.size() + ", events=" + recorded);
            return List.copyOf(recorded);
        }
    }

    public RecordingEventSink reset() {
        synchronized (recorded) {
            logger.info("RecordingEventSink[" + instanceId + "] RESET: size=" + recorded.size() + ", clearing events=" + recorded);
            recorded.clear();
        }
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("type", this.getClass().getSimpleName());
        descriptor.describeProperty("instanceId", instanceId);
        descriptor.describeProperty("delegate", delegate);
    }
}
