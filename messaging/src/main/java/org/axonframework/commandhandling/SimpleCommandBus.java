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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific command's name.
 * Interceptors may be configured to add processing to commands regardless of their type or name, for example logging,
 * security (authorization), sla monitoring, etc.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final List<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars;
    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>>> subscriptions = new ConcurrentHashMap<>();

    public SimpleCommandBus(ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        this(Arrays.asList(processingLifecycleHandlerRegistrars));
    }

    public SimpleCommandBus(Collection<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars) {
        this.processingLifecycleHandlerRegistrars = processingLifecycleHandlerRegistrars.isEmpty()
                ? emptyList()
                : new ArrayList<>(processingLifecycleHandlerRegistrars);
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                                         @Nullable ProcessingContext processingContext) {
        Optional<MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>>> optionalHandler = findCommandHandlerFor(
                command);
        if (optionalHandler.isPresent()) {
            return handle(command, optionalHandler.get());
        } else {
            return CompletableFuture.failedFuture(new NoHandlerForCommandException(format(
                    "No handler was subscribed for command [%s].",
                    command.getCommandName())));
        }
    }

    private Optional<MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>>> findCommandHandlerFor(
            CommandMessage<?> command) {
        return Optional.ofNullable(subscriptions.get(command.getCommandName()));
    }

    /**
     * Performs the actual handling logic.
     *
     * @param command The actual command to handle
     * @param handler The handler that must be invoked for this command
     */
    protected CompletableFuture<? extends CommandResultMessage<?>> handle(
            CommandMessage<?> command,
            MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling command [{}]", command.getCommandName());
        }

        AsyncUnitOfWork unitOfWork = new AsyncUnitOfWork();
        processingLifecycleHandlerRegistrars.forEach(it -> it.registerHandlers(unitOfWork));

        return unitOfWork.executeWithResult(c -> handler.handle(command, c).asCompletableFuture())
                         .thenApply(GenericCommandResultMessage::asCommandResultMessage);
    }

    /**
     * Subscribe the given {@code handler} to commands with given {@code commandName}. If a subscription already exists
     * for the given name, a {@link DuplicateCommandHandlerSubscriptionException} is thrown.
     *
     * @throws DuplicateCommandHandlerSubscriptionException when a subscription already exists for the given
     *                                                      commandName
     */
    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        assertNonNull(handler, "handler may not be null");
        logger.debug("Subscribing command with name [{}]", commandName);
        var existingHandler = subscriptions.putIfAbsent(commandName, handler);

        if (existingHandler != null && existingHandler != handler) {
            throw new DuplicateCommandHandlerSubscriptionException(commandName, existingHandler, handler);
        }

        return () -> subscriptions.remove(commandName, handler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("subscriptions", subscriptions);
        descriptor.describeProperty("lifecycleRegistrars", processingLifecycleHandlerRegistrars);
    }
}
