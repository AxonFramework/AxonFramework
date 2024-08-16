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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.Consumer;

/**
 * Interface describing the actions that can be taken on a transaction to append one or more events to the
 * {@link AsyncEventStore}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface AppendEventTransaction {

    /**
     *
     */
    ProcessingContext.ResourceKey<Long> APPEND_POSITION_KEY = ProcessingContext.ResourceKey.create("appendPosition");

    /**
     * Appends an {@code eventMessage} to be appended to an {@link AsyncEventStore} in this transaction with the given
     * {@code condition}.
     *
     * @param eventMessage The {@link EventMessage} to append.
     * @param condition    The consistency condition validated when...
     */
    //TODO does a condition per event make sense? Wouldn't this apply for the entire?
    void appendEvent(EventMessage<?> eventMessage, AppendCondition condition);

    /**
     * Registers a callback to invoke when an event is appended to this transaction.
     *
     * @param callback The callback to invoke when an event is appended.
     */
    void onAppend(Consumer<EventMessage<?>> callback);
}

// TODO use the onAppend callback to retrieve the sequence of the last appended event, or have a separate method for this?