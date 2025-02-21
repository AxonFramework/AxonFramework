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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * Interface describing a registry of {@link EventHandler event handlers}.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventHandlerRegistry {

    /**
     * Subscribe the given {@code handler} for {@link EventMessage events} of the given {@code names}.
     * <p>
     * If a subscription already exists for any {@link QualifiedName name} in the given set, the behavior is undefined.
     * Implementations may throw an exception to refuse duplicate subscription or alternatively decide whether the
     * existing or new {@code handler} gets the subscription.
     *
     * @param names        The names of the given {@code commandHandler} can handle.
     * @param eventHandler The handler instance that handles {@link EventMessage events} for the given names.
     * @return This registry for fluent interfacing.
     */
    default EventHandlerRegistry subscribe(@Nonnull Set<QualifiedName> names,
                                           @Nonnull EventHandler eventHandler) {
        names.forEach(name -> subscribe(name, eventHandler));
        return this;
    }

    /**
     * Subscribe the given {@code handler} for {@link EventMessage events} of the given {@code name}.
     * <p>
     * If a subscription already exists for the {@code name}, the behavior is undefined. Implementations may throw an
     * exception to refuse duplicate subscription or alternatively decide whether the existing or new {@code handler}
     * gets the subscription.
     *
     * @param name         The name the given {@code commandHandler} can handle.
     * @param eventHandler The handler instance that handles {@link EventMessage events} for the given name.
     * @return This registry for fluent interfacing.
     */
    EventHandlerRegistry subscribe(@Nonnull QualifiedName name,
                                   @Nonnull EventHandler eventHandler);

    /**
     * Subscribe the given {@code handlingComponent} with this registry.
     * <p>
     * Typically invokes {@link #subscribe(Set, EventHandler)}, using the
     * {@link EventHandlingComponent#supportedEvents()} as the set of compatible {@link QualifiedName names} the
     * component in question can deal with.
     *
     * @param handlingComponent The event handling component instance to subscribe with this registry.
     * @return This registry for fluent interfacing.
     */
    default EventHandlerRegistry subscribe(@Nonnull EventHandlingComponent handlingComponent) {
        return subscribe(handlingComponent.supportedEvents(), handlingComponent);
    }
}
