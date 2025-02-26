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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * Interface describing a registry of {@link CommandHandler command handlers}.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 * @param <S> the type of the registry itself, used for fluent interfacing
 */
public interface CommandHandlerRegistry<S extends CommandHandlerRegistry<S>> {

    /**
     * Subscribe the given {@code handler} for {@link CommandMessage commands} of the given {@code names}.
     * <p>
     * If a subscription already exists for any {@link QualifiedName name} in the given set, the behavior is undefined.
     * Implementations may throw an exception to refuse duplicate subscription or alternatively decide whether the
     * existing or new {@code handler} gets the subscription.
     *
     * @param names          The names of the given {@code commandHandler} can handle.
     * @param commandHandler The handler instance that handles {@link CommandMessage commands} for the given names.
     * @return This registry for fluent interfacing.
     */
    default S subscribe(@Nonnull Set<QualifiedName> names,
                        @Nonnull CommandHandler commandHandler) {
        names.forEach(name -> subscribe(name, commandHandler));
        //noinspection unchecked
        return (S) this;
    }

    /**
     * Subscribe the given {@code handler} for {@link CommandMessage commands} of the given {@code name}.
     * <p>
     * If a subscription already exists for the {@code name}, the behavior is undefined. Implementations may throw an
     * exception to refuse duplicate subscription or alternatively decide whether the existing or new {@code handler}
     * gets the subscription.
     *
     * @param name           The name the given {@code commandHandler} can handle.
     * @param commandHandler The handler instance that handles {@link CommandMessage commands} for the given name.
     * @return This registry for fluent interfacing.
     */
    S subscribe(@Nonnull QualifiedName name,
                @Nonnull CommandHandler commandHandler);


    /**
     * Subscribe the given {@code handlingComponent} with this registry.
     * <p>
     * Typically invokes {@link #subscribe(Set, CommandHandler)}, using the
     * {@link CommandHandlingComponent#supportedCommands()} as the set of compatible {@link QualifiedName names} the
     * component in question can deal with.
     * <p>
     * If a subscription already exists for any {@link QualifiedName name} in the supported command names, the behavior
     * is undefined. Implementations may throw an exception to refuse duplicate subscription or alternatively decide
     * whether the existing or new {@code handler} gets the subscription.
     *
     * @param handlingComponent The command handling component instance to subscribe with this registry.
     * @return This registry for fluent interfacing.
     */
    default S subscribe(@Nonnull CommandHandlingComponent handlingComponent) {
        return subscribe(handlingComponent.supportedCommands(), handlingComponent);
    }
}
