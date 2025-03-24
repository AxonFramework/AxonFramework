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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class RecordingAsyncEventStore implements AsyncEventStore {

    private AsyncEventStore delegate;
    private List<EventMessage<?>> recorded;

    public RecordingAsyncEventStore(AsyncEventStore delegate) {
        this.delegate = delegate;
        this.recorded = new ArrayList<>();
    }

    public RecordingAsyncEventStore(AsyncEventStore delegate, List<EventMessage<?>> recorded) {
        this.delegate = delegate;
        this.recorded = recorded;
    }

    @Override
    public EventStoreTransaction transaction(@NotNull ProcessingContext processingContext, @NotNull String context) {
        var tx = delegate.transaction(processingContext, context);
        tx.onAppend(recorded::add);
        return tx;
    }

    @Override
    public void describeTo(@NotNull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    @Override
    public CompletableFuture<Void> publish(@NotNull String context, @NotNull List<EventMessage<?>> events) {
        return delegate.publish(context, events);
    }

    public List<EventMessage<?>> recorded() {
        return List.copyOf(recorded);
    }
}
