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
import org.axonframework.commandhandling.CommandHandlerRegistry;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlingComponent;

import java.util.Set;

/**
 * Interface describing a generic handling component that might contain command, event, and query handlers.
 * <p>
 * As such, it allows registration of {@code CommandHandlers}, {@code EventHandlers}, and {@code QueryHandlers} through
 * the respective {@code CommandHandlingComponent}, {@code EventHandlingComponent}, and {@code QueryHandlingComponent}.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface MessageHandlingComponent
        extends CommandHandlingComponent, CommandHandlerRegistry<MessageHandlingComponent>,
        EventHandlingComponent, QueryHandlingComponent, MessageHandler {

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
    default MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                               @Nonnull MessageHandler handler) {
        switch (handler) {
            case MessageHandlingComponent component:
                component.supportedCommands()
                         .forEach(commandName -> subscribe(commandName, (CommandHandler) component));
                component.supportedEvents()
                         .forEach(eventName -> subscribe(eventName, (EventHandler) component));
                component.supportedQueries()
                         .forEach(queryName -> subscribe(queryName, (QueryHandler) component));
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

    /**
     * Subscribe the given {@code handlingComponent} with this component.
     * <p>
     * Typically invokes {@link #subscribe(Set, CommandHandler)}, {@link #subscribe(QualifiedName, EventHandler)}, and
     * {@link #subscribe(QualifiedName, QueryHandler)}, using the {@link CommandHandlingComponent#supportedCommands()},
     * {@link EventHandlingComponent#supportedEvents()}, and {@link QueryHandlingComponent#supportedQueries()}
     * respectively as the set of compatible {@link QualifiedName names} the component in question can deal with.
     *
     * @param handlingComponent The message handling component instance to subscribe with this component.
     * @return This component for fluent interfacing.
     */
    default MessageHandlingComponent subscribe(@Nonnull MessageHandlingComponent handlingComponent) {
        handlingComponent.supportedCommands()
                         .forEach(commandName -> subscribe(commandName, (CommandHandler) handlingComponent));
        handlingComponent.supportedEvents()
                         .forEach(eventName -> subscribe(eventName, (EventHandler) handlingComponent));
        handlingComponent.supportedQueries()
                         .forEach(queryName -> subscribe(queryName, (QueryHandler) handlingComponent));
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
}