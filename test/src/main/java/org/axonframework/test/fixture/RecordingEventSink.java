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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
public class RecordingEventSink implements EventSink {

    private static final Logger logger = LoggerFactory.getLogger(RecordingEventSink.class);

    private final List<EventMessage> recorded = new CopyOnWriteArrayList<>();
    protected final EventSink delegate;

    /**
     * Creates a new {@code RecordingEventSink} that will record all events published to the given {@code delegate}.
     *
     * @param delegate The {@link EventSink} to which events will be published.
     */
    public RecordingEventSink(@Nonnull EventSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventSink may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        logger.debug("publish() called with {} event(s): {}",
                     events.size(),
                     events.stream().map(e -> e.payloadType().getSimpleName() + "[" + e.identifier() + "]").toList());
        logger.debug("publish() - delegating to {} on thread {}",
                     delegate.getClass().getSimpleName(),
                     Thread.currentThread().getName());

        return delegate.publish(context, events)
                       .thenRun(() -> {
                           recorded.addAll(events);
                           logger.debug(
                                   "publish() - thenRun completed, recorded {} event(s), total recorded: {} on thread {}",
                                   events.size(),
                                   recorded.size(),
                                   Thread.currentThread().getName());
                       });
    }

    /**
     * Returns a copied list of all the {@link EventMessage EventMessages}
     * {@link #publish(ProcessingContext, List) published}.
     *
     * @return A copied list of all the {@link EventMessage EventMessages}
     * {@link #publish(ProcessingContext, List) published}.
     */
    public List<EventMessage> recorded() {
        logger.debug("recorded() called, returning {} event(s): {} on thread {}",
                     recorded.size(),
                     recorded.stream().map(e -> e.payloadType().getSimpleName() + "[" + e.identifier() + "]").toList(),
                     Thread.currentThread().getName());
        return List.copyOf(recorded);
    }

    /**
     * Resets this recording {@link EventSink}, by removing all recorded {@link EventMessage EventMessages}.
     *
     * @return This recording {@link EventSink}, for fluent interfacing.
     */
    public RecordingEventSink reset() {
        logger.debug("reset() called, clearing {} recorded event(s) on thread {}",
                     recorded.size(),
                     Thread.currentThread().getName());
        this.recorded.clear();
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("recorded", recorded);
    }
}
