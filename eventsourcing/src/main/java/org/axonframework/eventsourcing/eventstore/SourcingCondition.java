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
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.OptionalLong;

/**
 * Interface describing the condition to
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) source} events from an Event Store.
 * <p>
 * The condition has a mandatory {@link #criteria()} used to retrieve the exact sequence of events to source the
 * model(s). The {@link #start()} and {@link #end()} operations define the window of events that the
 * {@link EventStoreTransaction} is interested in. Use these fields to retrieve slices of the model(s) to source.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface SourcingCondition {

    /**
     * Construct a {@link SourcingCondition} used to source a model based on the given {@code index}.
     *
     * @param index The {@link Index} used as part of the {@link SourcingCondition#criteria()}.
     * @return A {@link SourcingCondition} that will retrieve an event sequence matching the given {@code index}.
     */
    static SourcingCondition conditionFor(@Nonnull Index index) {
        return conditionFor(index, -1L);
    }

    /**
     * Construct a {@link SourcingCondition} used to source a model based on the given {@code index}.
     * <p>
     * Will start the sequence at the given {@code start} value.
     *
     * @param index The {@link Index} used as part of the {@link SourcingCondition#criteria()}.
     * @param start The start position in the event sequence to retrieve of the model to source.
     * @return A {@link SourcingCondition} that will retrieve an event sequence matching the given {@code index},
     * starting at the given {@code start}.
     */
    static SourcingCondition conditionFor(@Nonnull Index index,
                                          Long start) {
        return conditionFor(index, start, Long.MAX_VALUE);
    }

    /**
     * Construct a {@link SourcingCondition} used to source a model based on the given {@code index}.
     * <p>
     * Will start the sequence at the given {@code start} value and cut it off at the given {@code end} value.
     *
     * @param index The {@link Index} used as part of the {@link SourcingCondition#criteria()}.
     * @param start The start position in the event sequence to retrieve of the model to source.
     * @param end   The end position in the event sequence to retrieve of the model to source.
     * @return A {@link SourcingCondition} that will retrieve an event sequence matching the given {@code index},
     * starting at the given {@code start} and ending at the given {@code end}.
     */
    static SourcingCondition conditionFor(@Nonnull Index index,
                                          Long start,
                                          Long end) {
        return new DefaultSourcingCondition(EventCriteria.hasIndex(index), start, end);
    }

    /**
     * The {@link EventCriteria} used to source an event sequence complying to its criteria.
     *
     * @return The {@link EventCriteria} used to retrieve an event sequence complying to its criteria.
     */
    EventCriteria criteria();

    /**
     * The start position in the event sequence to source. Defaults to {@code -1L} to ensure we start at the beginning
     * of the sequence's stream complying to the {@link #criteria()}.
     *
     * @return The start position in the event sequence to source.
     */
    default long start() {
        return -1L;
    }

    /**
     * The end position in the event sequence to source. Defaults to an {@link OptionalLong#empty() empty optional} to
     * ensure we take the entire event sequence complying to the {@link #criteria()}
     *
     * @return The end position in the event sequence to source.
     */
    default OptionalLong end() {
        return OptionalLong.empty();
    }

    /**
     * Combine the {@link #criteria()}, {@link #start()}, and {@link #end()} of {@code this SourcingCondition} with the
     * {@code criteria}, {@code start}, and {@code end} of the given {@code other SourcingCondition}.
     * <p>
     * Typically, the minimum value of both {@code start} values and the maximum value of both {@code end} values will
     * be part of the end result.
     *
     * @param other The {@link SourcingCondition} to combine with {@code this SourcingCondition}.
     * @return A combined {@link SourcingCondition} based on {@code this SourcingCondition} and the given {@code other}.
     */
    SourcingCondition combine(@Nonnull SourcingCondition other);
}
