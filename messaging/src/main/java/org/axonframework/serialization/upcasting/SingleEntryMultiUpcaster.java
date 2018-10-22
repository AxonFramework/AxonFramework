/*
 * Copyright (c) 2010-2018. Axon Framework
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

/**
 * Abstract implementation of an {@link Upcaster} that eases the common process of upcasting one intermediate
 * representation to several other representations by applying a simple flat mapping function to the input stream of
 * intermediate representations.
 *
 * @author Steven van Beelen
 * @since 3.0.6
 */
public abstract class SingleEntryMultiUpcaster<T> implements Upcaster<T> {

    @Override
    public Stream<T> upcast(Stream<T> intermediateRepresentations) {
        return intermediateRepresentations.flatMap(entry -> {
            if (!canUpcast(entry)) {
                return Stream.of(entry);
            }
            return doUpcast(entry);
        });
    }

    /**
     * Checks if this upcaster can upcast the given {@code intermediateRepresentation}.
     * If the upcaster cannot upcast the representation the {@link #doUpcast(T)} is not invoked.
     *
     * @param intermediateRepresentation the intermediate object representation to upcast
     * @return {@code true} if the representation can be upcast, {@code false} otherwise
     */
    protected abstract boolean canUpcast(T intermediateRepresentation);

    /**
     * Upcasts the given {@code intermediateRepresentation}.
     * This method is only invoked if {@link #canUpcast(T)} returned {@code true} for the given representation.
     * <p>
     * Note that the returned representation should not be {@code null}.
     * To remove an intermediateRepresentation add a filter to the input stream.
     *
     * @param intermediateRepresentation the representation of the object to upcast
     * @return the upcasted representations as a {@code Stream} with generic type {@code T}
     */
    protected abstract Stream<T> doUpcast(T intermediateRepresentation);
}
