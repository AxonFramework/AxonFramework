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

package org.axonframework.eventsourcing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * An {@link EntityEvolver} implementation that converts the {@link EventMessage#getPayload()} to the given
 * {@code payloadType} to evolve an entity with.
 * <p>
 * Will throw a {@link ClassCastException} if the {@code payloadType} does not match.
 *
 * @param <P> The payload type of the event to apply.
 * @param <E> The entity type to evolve.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PayloadConvertingEntityEvolver<P, E> implements EntityEvolver<E> {

    private final Class<P> payloadType;
    private final BiFunction<E, P, E> evolver;

    /**
     * Constructs a {@code PayloadConvertingEntityEvolver}, converting the {@link EventMessage#getPayload()} to the
     * given {@code payloadType}, after which it invokes the given {@code evolver}.
     * <p>
     * If the {@link EventMessage#getPayload()} cannot be converted to the requested {@code payloadType}, a
     * {@link ClassCastException} is thrown.
     *
     * @param payloadType The payload type to check against.
     * @param evolver     The function to evolve the entity with.
     */
    public PayloadConvertingEntityEvolver(@Nonnull Class<P> payloadType,
                                          @Nonnull BiFunction<E, P, E> evolver) {
        this.payloadType = requireNonNull(payloadType, "The payload type must not be null.");
        this.evolver = requireNonNull(evolver, "The evolver must not be null.");
    }

    @Override
    public E evolve(@Nonnull E entity,
                    @Nonnull EventMessage<?> event,
                    @Nonnull ProcessingContext context) {
        P payload = payloadType.cast(event.getPayload());
        return evolver.apply(entity, payload);
    }
}
