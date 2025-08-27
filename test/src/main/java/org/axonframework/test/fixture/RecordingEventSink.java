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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

    protected final EventSink delegate;
    private final List<EventMessage> recorded;

    /**
     * Creates a new {@link RecordingEventSink} that will record all events published to the given {@code delegate}.
     * @param delegate The {@link EventSink} to which events will be published.
     */
    public RecordingEventSink(@Nonnull EventSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventSink may not be null");
        this.recorded = new ArrayList<>();
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        return delegate.publish(context, events)
                       .thenRun(() -> recorded.addAll(events));
    }

    public List<EventMessage> recorded() {
        return List.copyOf(recorded);
    }

    public RecordingEventSink reset() {
        this.recorded.clear();
        return this;
    }
}
