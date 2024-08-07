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

import java.util.function.Consumer;

/**
 * Interface describing the actions that can be taken on a transaction to append one or more events to the
 * {@link EventStore}.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public interface AppendEventTransaction {

    /**
     * Appends an {@code eventMessage} to be appended to an {@link EventStore} in this transaction.
     *
     * @param eventMessage The {@link EventMessage} to append.
     */
    // TODO - Add tags/labels/indices/association to the appendEvent operation
    void appendEvent(EventMessage<?> eventMessage);

    /**
     * Registers a callback to invoke when an event is appended to this transaction.
     *
     * @param callback The callback to invoke when an event is appended.
     */
    void onEvent(Consumer<EventMessage<?>> callback);
}
