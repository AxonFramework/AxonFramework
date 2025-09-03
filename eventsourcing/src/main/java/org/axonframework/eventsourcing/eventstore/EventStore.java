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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;

/**
 * Infrastructure component providing the means to start an {@link EventStoreTransaction} to
 * {@link EventStoreTransaction#appendEvent(EventMessage) append events} and
 * {@link EventStoreTransaction#source(SourcingCondition) event source} models from the underlying storage solution.
 * <p>
 * As an implementation of the {@link EventSink}, this {@code EventStore} will initiate a
 * {@link #transaction(ProcessingContext)} when {@link #publish(ProcessingContext, List)} is triggered to append events.
 * When a {@code null ProcessingContext} is given on {@link #publish(ProcessingContext, List)}, the implementation
 * should decide to construct a context itself or fail outright.
 * <p>
 * As an implementation of the {@link StreamableEventSource}, this {@code EventStore} will allow for {@link #open} a
 * stream of events and use it as a source for
 * {@link org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 0.1.0
 */
public interface EventStore extends StreamableEventSource<EventMessage>, EventSink, DescribableComponent {

    /**
     * Retrieves the {@link EventStoreTransaction transaction for appending events} for the given
     * {@code processingContext}. If no transaction is available, a new, empty transaction is created.
     *
     * @param processingContext The context for which to retrieve the {@link EventStoreTransaction}.
     * @return The {@link EventStoreTransaction}, existing or newly created, for the given {@code processingContext}.
     */
    EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext);
}
