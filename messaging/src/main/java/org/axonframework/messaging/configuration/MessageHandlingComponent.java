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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.common.CollectionUtils;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlingComponent;

import java.util.HashSet;
import java.util.Set;

/**
 * Interface describing a generic handling component that might contain command, event, and query handlers.
 * <p>
 * As such, it allows registration of {@code CommandHandlers}, {@code EventHandlers}, and {@code QueryHandlers} through
 * the respective {@code CommandHandlingComponent}, {@code EventHandlingComponent}, and {@code QueryHandlingComponent}.
 * Besides handling and registration, it specifies which {@link #supportedMessages() messages} it supports.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public non-sealed interface MessageHandlingComponent
        extends CommandHandlingComponent, EventHandlingComponent, QueryHandlingComponent, MessageHandler {

    /**
     * Subscribe the given {@code handler} for {@link org.axonframework.messaging.Message messages} of the given
     * {@code names}.
     * <p>
     * Will differentiate automatically whether the given {@code handler} is for commands, events, or queries.
     * <p>
     * If a subscription already exists for any {@link QualifiedName name} in the given set, the behavior is undefined.
     * Implementations may throw an exception to refuse duplicate subscription or alternatively decide whether the
     * existing or new {@code handler} gets the subscription.
     *
     * @param names   The names of the given {@code handler} can handle.
     * @param handler The handler instance that handles {@link org.axonframework.messaging.Message messages} for the
     *                given names.
     * @return This registry for fluent interfacing.
     */
    // TODO discuss if we want chaining, or Registration objects
    default MessageHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                               @Nonnull MessageHandler handler) {
        names.forEach(n -> subscribe(n, handler));
        return this;
    }

    /**
     * Subscribe the given {@code handler} for {@link org.axonframework.messaging.Message messages} of the given
     * {@code name}.
     * <p>
     * Will differentiate automatically whether the given {@code handler} is for commands, events, or queries.
     * <p>
     * If a subscription already exists for the {@code name}, the behavior is undefined. Implementations may throw an
     * exception to refuse duplicate subscription or alternatively decide whether the existing or new {@code handler}
     * gets the subscription.
     *
     * @param name    The name the given {@code handler} can handle.
     * @param handler The handler instance that handles {@link org.axonframework.messaging.Message messages} for the
     *                given name.
     * @return This registry for fluent interfacing.
     */
    // TODO discuss if we want chaining, or Registration objects
    default MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                               @Nonnull MessageHandler handler) {
        switch (handler) {
            case MessageHandlingComponent component:
                component.supportedCommands().forEach(command -> subscribe(command, (CommandHandler) component));
                component.supportedEvents().forEach(command -> subscribe(command, (EventHandler) component));
                component.supportedQueries().forEach(command -> subscribe(command, (QueryHandler) component));
                break;
            case CommandHandler commandHandler:
                subscribe(name, commandHandler);
                break;
            case EventHandler eventHandler:
                subscribe(name, eventHandler);
                break;
            case QueryHandler queryHandler:
                subscribe(name, queryHandler);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + handler);
        }
        return this;
    }

    @Override
    MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                       @Nonnull CommandHandler commandHandler);

    default MessageHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                               @Nonnull CommandHandler commandHandler) {
        names.forEach(name -> subscribe(name, commandHandler));
        return this;
    }

    @Override
    MessageHandlingComponent subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler);

    @Override
    default MessageHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                               @Nonnull EventHandler eventHandler) {
        names.forEach(name -> subscribe(name, eventHandler));
        return this;
    }

    @Override
    MessageHandlingComponent subscribe(@Nonnull QualifiedName name, @Nonnull QueryHandler queryHandler);

    @Override
    default MessageHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                               @Nonnull QueryHandler queryHandler) {
        names.forEach(name -> subscribe(name, queryHandler));
        return this;
    }

    /**
     * All supported {@link org.axonframework.messaging.Message messages}, referenced through a {@link QualifiedName}.
     *
     * @return All supported {@link org.axonframework.messaging.Message messages}, referenced through a
     * {@link QualifiedName}.
     */
    default Set<QualifiedName> supportedMessages() {
        Set<QualifiedName> supportedCommandsAndEvents = CollectionUtils.merge(supportedCommands(),
                                                                              supportedEvents(),
                                                                              HashSet::new);
        return CollectionUtils.merge(supportedCommandsAndEvents, supportedQueries(), HashSet::new);
    }
}
