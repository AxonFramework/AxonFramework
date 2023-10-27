/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.serialization.upcasting;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Abstract implementation of an {@link Upcaster} that eases the common process of upcasting one intermediate
 * representation to another representation by applying a simple mapping function to the input stream of intermediate
 * representations. Additionally, it's a context aware implementation, which enables it to store and reuse context
 * information from one entry to another during upcasting.
 *
 * @param <T> the type of entry to be upcasted as {@code T}
 * @param <C> the type of context used as {@code C}
 * @author Steven van Beelen
 * @since 3.1
 */
public abstract class ContextAwareSingleEntryUpcaster<T, C> implements Upcaster<T> {

    @Override
    public Stream<T> upcast(Stream<T> intermediateRepresentations) {
        C context = buildContext();
        return intermediateRepresentations.map(entry -> {
            if (!canUpcast(entry, context)) {
                return entry;
            }
            return requireNonNull(
                    doUpcast(entry, context),
                    "Result from #doUpcast() should not be null. "
                            + "To remove an intermediateRepresentation add a filter to the input stream."
            );
        });
    }

    /**
     * Checks if this upcaster can upcast the given {@code intermediateRepresentation}. If the upcaster cannot upcast
     * the representation the {@link #doUpcast(Object, Object)} is not invoked. The {@code context} can be used to store
     * or retrieve entry specific information required to make the {@code canUpcast(Object, Object)} check.
     *
     * @param intermediateRepresentation the intermediate object representation to upcast as {@code T}
     * @param context                    the context for this upcaster as {@code C}
     * @return {@code true} if the representation can be upcast, {@code false} otherwise
     */
    protected abstract boolean canUpcast(T intermediateRepresentation, C context);

    /**
     * Upcasts the given {@code intermediateRepresentation}. This method is only invoked if {@link #canUpcast(Object,
     * Object)} returned {@code true} for the given representation. The {@code context} can be used to store or retrieve
     * entry specific information required to perform the upcasting process.
     * <p>
     * Note that the returned representation should not be {@code null}. To remove an intermediateRepresentation add a
     * filter to the input stream.
     *
     * @param intermediateRepresentation the representation of the object to upcast as {@code T}
     * @param context                    the context for this upcaster as {@code C}
     * @return the upcasted representation
     */
    protected abstract T doUpcast(T intermediateRepresentation, C context);

    /**
     * Builds a context of generic type {@code C} to be used when processing the stream of intermediate object
     * representations {@code T}.
     *
     * @return a context of generic type {@code C}
     */
    protected abstract C buildContext();
}
