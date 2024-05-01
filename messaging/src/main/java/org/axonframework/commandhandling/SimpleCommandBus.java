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

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
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
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific command's
 * name.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final List<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars;
    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>, ? extends Message<?>>> subscriptions = new ConcurrentHashMap<>();
    // TODO - Instead of using an Executor, we should use a WorkerFactory to allow more flexible creation (and disposal) of workers
    private final Executor worker;

    public SimpleCommandBus(ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        this(DirectExecutor.instance(), processingLifecycleHandlerRegistrars);
    }

    public SimpleCommandBus(Executor workerSupplier,
                            ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        this(workerSupplier, Arrays.asList(processingLifecycleHandlerRegistrars));
    }

    public SimpleCommandBus(Executor workerSupplier,
                            Collection<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars) {
        this.worker = workerSupplier;
        this.processingLifecycleHandlerRegistrars = processingLifecycleHandlerRegistrars.isEmpty()
                ? emptyList()
                : new ArrayList<>(processingLifecycleHandlerRegistrars);
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        return findCommandHandlerFor(command)
                .map(handler -> handle(command, handler))
                .orElseGet(() -> CompletableFuture.failedFuture(new NoHandlerForCommandException(format(
                        "No handler was subscribed for command [%s].", command.getCommandName()))));
    }

    private Optional<MessageHandler<? super CommandMessage<?>, ? extends Message<?>>> findCommandHandlerFor(
            CommandMessage<?> command) {
        return Optional.ofNullable(subscriptions.get(command.getCommandName()));
    }

    /**
     * Performs the actual handling logic.
     *
     * @param command The actual command to handle
     * @param handler The handler that must be invoked for this command
     */
    protected CompletableFuture<? extends Message<?>> handle(
            CommandMessage<?> command,
            MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling command [{} ({})]", command.getIdentifier(), command.getCommandName());
        }

        AsyncUnitOfWork unitOfWork = new AsyncUnitOfWork(command.getIdentifier(), worker);
        processingLifecycleHandlerRegistrars.forEach(it -> it.registerHandlers(unitOfWork));

        var result = unitOfWork.executeWithResult(c -> handler.handle(command, c).asCompletableFuture());
        if (logger.isDebugEnabled()) {
            result = result.whenComplete((r, e) -> {
                if (e == null) {
                    logger.debug("Command [{} ({})] completed successfully",
                                 command.getIdentifier(),
                                 command.getCommandName());
                } else {
                    logger.debug("Command [{} ({})] completed exceptionally",
                                 command.getIdentifier(),
                                 command.getCommandName(),
                                 e);
                }
            });
        }
        return result;
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
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler) {
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
        descriptor.describeProperty("worker", worker);
        descriptor.describeProperty("subscriptions", subscriptions);
        descriptor.describeProperty("lifecycleRegistrars", processingLifecycleHandlerRegistrars);
    }
}
