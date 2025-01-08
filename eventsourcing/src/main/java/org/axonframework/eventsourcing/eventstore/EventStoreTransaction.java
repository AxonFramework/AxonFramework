/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.Consumer;

/**
 * Interface describing the actions that can be taken on a transaction to source a model from the
 * {@link AsyncEventStore} based on the resulting {@link MessageStream}.
 * <p>
 * Note that this transaction includes operations for {@link #source(SourcingCondition, ProcessingContext) sourcing} the
 * model as well as {@link #appendEvent(EventMessage) appending events}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventStoreTransaction {

    /**
     * Sources a {@link MessageStream} of type {@link EventMessage} based on the given {@code condition} that can be
     * used to rehydrate a model.
     * <p>
     * The given {@code context} is used to perform tasks at the right
     * {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase phases} and register resources throughout
     * its lifecycle. All {@code condition} instances provided to source as part of the given {@code context} should be
     * used to derive the {@link AppendCondition} typically used by the {@link #appendEvent(EventMessage)} method's
     * implementation.
     *
     * @param condition The {@link SourcingCondition} used to retrieve the {@link MessageStream} containing the sequence
     *                  of events that can rehydrate a model.
     * @param context   The {@link ProcessingContext} used to perform tasks at the right
     *                  {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase phases} and register
     *                  resources throughout its lifecycle.
     * @return The {@link MessageStream} of type {@link EventMessage} containing to the event sequence complying to the
     * given {@code condition}.
     */
    MessageStream<? extends EventMessage<?>> source(@Nonnull SourcingCondition condition,
                                                    @Nonnull ProcessingContext context);

    /**
     * Appends an {@code eventMessage} to be appended to an {@link AsyncEventStore} in this transaction with the given
     * {@code condition}.
     *
     * @param eventMessage The {@link EventMessage} to append.
     */
    void appendEvent(@Nonnull EventMessage<?> eventMessage);

    /**
     * Registers a {@code callback} to invoke when an event is {@link #appendEvent(EventMessage) appended} to this
     * transaction.
     * <p>
     * Each {@code callback} registration adds a new callback that is invoked on the
     * {@code appendEvent(EventMessage, AppendCondition)} operation.
     *
     * @param callback A {@link Consumer} to invoke when an event is appended in this transaction.
     */
    void onAppend(@Nonnull Consumer<EventMessage<?>> callback);

    /**
     * Returns the position in the event store of the last {@link #appendEvent(EventMessage) appended} event by this
     * transaction.
     * <p>
     * Will return {@code -1L} if nothing has been appended yet.
     *
     * @param context The {@link ProcessingContext} for which to retrieve the last appended event for
     * @return The position in the event store of the last {@link #appendEvent(EventMessage) appended} event by this
     * transaction.
     */
    long appendPosition(@Nonnull ProcessingContext context);
}