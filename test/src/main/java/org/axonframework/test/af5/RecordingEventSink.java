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

package org.axonframework.test.af5;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.messaging.unitofwork.ProcessingContext;

class RecordingEventSink implements EventSink {

    private final EventSink delegate;
    private final List<EventMessage<?>> recorded;

    RecordingEventSink(EventSink delegate) {
        this.delegate = delegate;
        this.recorded = new ArrayList<>();
    }

    @Override
    public void publish(ProcessingContext processingContext, @NotNull String context,
                        @NotNull List<EventMessage<?>> events) {
        recorded.addAll(events);
        delegate.publish(processingContext, context, events);
    }

    @Override
    public CompletableFuture<Void> publish(@NotNull String context, @NotNull List<EventMessage<?>> events) {
        return delegate.publish(context, events)
                       .thenRun(() -> recorded.addAll(events));
    }

    public List<EventMessage<?>> recorded() {
        return List.copyOf(recorded);
    }

    public RecordingEventSink reset() {
        this.recorded.clear();
        return this;
    }
}
