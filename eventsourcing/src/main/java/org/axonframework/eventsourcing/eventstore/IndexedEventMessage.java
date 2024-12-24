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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * An {@link EventMessage} containing {@link Index Indices}.
 * <p>
 * {@code Indices} typically refer to the name and value of the identifiers of the models that decided to publish this
 * event.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link IndexedEventMessage}.
 * @author Allard Buijze
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface IndexedEventMessage<P> extends EventMessage<P> {

    /**
     * Converts the given {@code event} into an {@link IndexedEventMessage} by adding the given {@code indices} to it.
     * <p>
     * If the {@code event} is already an {@link IndexedEventMessage}, the {@code indices} are added to the existing
     * {@link #indices()}.
     *
     * @param event   The {@link EventMessage} to convert into an {@link IndexedEventMessage}, combined with the given
     *                {@code indices}.
     * @param indices The {@link Set} of {@link Index Indices} to set for the given {@code event} that is being
     *                converted.
     * @param <P>     The type of payload carried by the given {@code event}.
     * @return An {@link IndexedEventMessage} based on the given {@code event} and {@code indices}.
     */
    static <P> IndexedEventMessage<P> asIndexedEvent(@Nonnull EventMessage<P> event,
                                                     @Nonnull Set<Index> indices) {
        return event instanceof IndexedEventMessage<P> taggedEvent
                ? taggedEvent.updateIndices(mergeWith(indices))
                : new GenericIndexedEventMessage<>(event, indices);
    }

    private static Function<Set<Index>, Set<Index>> mergeWith(Set<Index> indices) {
        return oldIndices -> {
            HashSet<Index> mutableIndices = new HashSet<>(oldIndices);
            mutableIndices.addAll(indices);
            return new HashSet<>(mutableIndices);
        };
    }

    /**
     * Return the {@link Set} of {@link Index Indices} of this indexed {@link EventMessage}.
     *
     * @return The {@link Set} of {@link Index Indices} of this indexed {@link EventMessage}.
     */
    Set<Index> indices();

    /**
     * Construct a new {@link IndexedEventMessage} using the given {@code updater} to adjust the {@link #indices()} of
     * the new event.
     *
     * @param updater The {@link Function} returning a new {@link Set} of {@link Index Indices} based on the existing
     *                {@link #indices()}.
     * @return A new {@link IndexedEventMessage} using the given {@code updater} to adjust the {@link #indices()} of the
     * new event.
     */
    IndexedEventMessage<P> updateIndices(@Nonnull Function<Set<Index>, Set<Index>> updater);
}
