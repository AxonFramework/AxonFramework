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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import javax.annotation.Nonnull;

/**
 * Functional interface describing state changes made on a model of type {@code M} based on a given
 * {@link EventMessage}.
 *
 * @param <M> The model to apply the event state to.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface EventStateApplier<M> {

    /**
     * Change the state of the given {@code model} by applying the given {@code event} to it.
     *
     * @param event The event that might adjust the {@code model}.
     * @param model The current state of the entity to apply the given {@code event} to.
     * @return The changed stated based on the given {@code event}.
     */
    M apply(@Nonnull M model, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext processingContext);
}
