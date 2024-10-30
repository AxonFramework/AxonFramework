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
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Infrastructure component providing the means to start an {@link EventStoreTransaction} to
 * {@link EventStoreTransaction#appendEvent(EventMessage) append events} and
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) event source} models from the underlying
 * storage solution.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 0.1
 */ // TODO Rename to EventStore once fully integrated
public interface AsyncEventStore extends DescribableComponent {

    /**
     * Retrieves the {@link EventStoreTransaction transaction for appending events} for the given
     * {@code processingContext}. If no transaction is available, a new, empty transaction is created.
     * <p>
     * The given {@code context} ensure the returned {@code EventStoreTransaction}
     * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sources} and
     * {@link EventStoreTransaction#appendEvent(EventMessage) appends} from the desired location.
     *
     * @param processingContext The context for which to retrieve the {@link EventStoreTransaction}.
     * @param context           The (bounded) context for which to retrieve the {@link EventStoreTransaction}.
     * @return The {@link EventStoreTransaction}, existing or newly created, for the given {@code processingContext} and
     * {@code context}.
     */
    EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext,
                                      @Nonnull String context);
}
