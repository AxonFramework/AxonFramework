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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;

import java.util.List;

/**
 * Infrastructure component providing the means to start an {@link EventStoreTransaction} to
 * {@link EventStoreTransaction#appendEvent(EventMessage) append events} and
 * {@link EventStoreTransaction#source(SourcingCondition) event source} models from the underlying storage solution.
 * <p>
 * As an extension of the {@link EventBus}, this {@code EventStore} serves as both the event storage mechanism and
 * the event distribution mechanism. This dual role allows the EventStore to persist events durably while simultaneously
 * distributing them to subscribed event handlers, eliminating the need for a separate {@link EventBus} component in
 * event sourcing scenarios. Through the {@link SubscribableEventSource} capability inherited
 * from EventBus, components can {@link SubscribableEventSource#subscribe(java.util.function.BiFunction) subscribe}
 * to receive events as they are stored. The exact timing of when events are published to subscribers is
 * implementation-dependent and may occur within the same transaction if the {@link ProcessingContext} is shared between
 * the storage and distribution operations.
 * <p>
 * As an implementation of the {@link EventSink}, this {@code EventStore} will initiate a
 * {@link #transaction(ProcessingContext)} when {@link #publish(ProcessingContext, List)} is triggered to append events.
 * When a {@code null ProcessingContext} is given on {@link #publish(ProcessingContext, List)}, the implementation
 * should decide to construct a context itself or fail outright.
 * <p>
 * As an implementation of the {@link StreamableEventSource}, this {@code EventStore} will allow for {@link #open} a
 * stream of events and use it as a source for
 * {@link StreamingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 0.1.0
 */
public interface EventStore extends StreamableEventSource, EventBus, DescribableComponent {

    /**
     * Retrieves the {@link EventStoreTransaction transaction for appending events} for the given
     * {@code processingContext}. If no transaction is available, a new, empty transaction is created.
     * <p>
     * When using this method, the append criteria for consistency checking is automatically derived from the
     * criteria used for {@link EventStoreTransaction#source(SourcingCondition) sourcing} events. This is the default
     * behavior where the same criteria is used for both loading events and checking consistency.
     *
     * @param processingContext The context for which to retrieve the {@link EventStoreTransaction}.
     * @return The {@link EventStoreTransaction}, existing or newly created, for the given {@code processingContext}.
     * @see #transaction(EventCriteria, ProcessingContext)
     */
    default EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        return transaction(null, processingContext);
    }

    /**
     * Creates a transaction with an explicit {@link EventCriteria} for consistency checking when appending events.
     * <p>
     * This method enables Dynamic Consistency Boundaries (DCB) where the criteria used for
     * {@link EventStoreTransaction#source(SourcingCondition) sourcing} events (loading state) can differ from the
     * criteria used for consistency checking (appending events).
     * <p>
     * <b>Understanding {@link ConsistencyMarker} vs {@link EventCriteria} (Orthogonal Concerns):</b>
     * <p>
     * The {@link ConsistencyMarker} and the append {@link EventCriteria} are two orthogonal concerns that together
     * define the {@link AppendCondition}:
     * <ul>
     *   <li><b>{@link ConsistencyMarker}</b>: Represents the "read position" in the event stream.
     *       It is always extracted from the events returned by
     *       {@link EventStoreTransaction#source(SourcingCondition)}. This tells the system:
     *       <em>"I have seen events up to this position."</em></li>
     *   <li><b>Append {@link EventCriteria}</b>: Specifies WHICH events to check for conflicts after
     *       the marker position. This tells the system:
     *       <em>"Check if any events matching this criteria exist after my read position."</em></li>
     * </ul>
     * <p>
     * Because the {@link ConsistencyMarker} is always determined by the actual read position (extracted from sourced
     * events), only the {@link EventCriteria} needs to be provided here. The marker will be set automatically during
     * {@link EventStoreTransaction#source(SourcingCondition) sourcing}.
     * <p>
     * <b>Example - Accounting Use Case:</b>
     * <pre>{@code
     * // Source: Load CreditsIncreased AND CreditsDecreased to calculate balance
     * // Append: Only check for conflicts on CreditsDecreased (allow concurrent increases)
     *
     * EventCriteria sourceCriteria = EventCriteria.havingTags("accountId", id)
     *     .andBeingOneOfTypes("CreditsIncreased", "CreditsDecreased");
     * EventCriteria appendCriteria = EventCriteria.havingTags("accountId", id)
     *     .andBeingOneOfTypes("CreditsDecreased");
     *
     * eventStore.transaction(appendCriteria, context)
     *     .source(SourcingCondition.conditionFor(sourceCriteria))
     *     .reduce(...);
     * }</pre>
     *
     * @param appendCriteria   The {@link EventCriteria} defining which events to check for conflicts when appending.
     *                         The {@link ConsistencyMarker} will be determined automatically from sourced events.
     *                         If {@code null}, the append criteria will be derived from the source criteria
     *                         (same behavior as {@link #transaction(ProcessingContext)}).
     * @param processingContext The {@link ProcessingContext} for this transaction.
     * @return An {@link EventStoreTransaction} configured with the explicit append criteria.
     * @see #transaction(ProcessingContext) for default behavior where append criteria is derived from source criteria
     */
    EventStoreTransaction transaction(@Nullable EventCriteria appendCriteria,
                                      @Nonnull ProcessingContext processingContext);
}
