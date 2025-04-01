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
import jakarta.annotation.Nullable;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.QualifiedName;
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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of the {@code CommandBus} that {@link #dispatch(CommandMessage, ProcessingContext) dispatches}
 * commands to the handlers subscribed to that specific command's {@link QualifiedName name}. Furthermore, it is in
 * charge of invoking the {@link #subscribe(QualifiedName, CommandHandler) subscribed}
 * {@link CommandHandler command handlers} when a command is being dispatched.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final List<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars;
    private final ConcurrentMap<QualifiedName, CommandHandler> subscriptions = new ConcurrentHashMap<>();
    // TODO - Instead of using an Executor, we should use a WorkerFactory to allow more flexible creation (and disposal) of workers
    private final Executor worker;

    /**
     * Construct a {@code SimpleCommandBus}, using the given {@code processingLifecycleHandlerRegistrars} when
     * constructing a {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to handle commands in.
     *
     * @param processingLifecycleHandlerRegistrars A vararg of {@code ProcessingLifecycleHandlerRegistrar} instance,
     *                                             used when constructing a
     *                                             {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to
     *                                             handle commands in.
     */
    public SimpleCommandBus(ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        this(DirectExecutor.instance(), processingLifecycleHandlerRegistrars);
    }

    /**
     * Construct a {@code SimpleCommandBus}, using the given {@code processingLifecycleHandlerRegistrars} when
     * constructing a {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to handle commands in.
     *
     * @param workerSupplier                       The {@code Executor} used to handle commands in a dedicated
     *                                             {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle}.
     * @param processingLifecycleHandlerRegistrars A vararg of {@code ProcessingLifecycleHandlerRegistrar} instance,
     *                                             used when constructing a
     *                                             {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to
     *                                             handle commands in.
     */
    public SimpleCommandBus(@Nonnull Executor workerSupplier,
                            ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        this(workerSupplier, Arrays.asList(processingLifecycleHandlerRegistrars));
    }

    /**
     * Construct a {@code SimpleCommandBus}, using the given {@code processingLifecycleHandlerRegistrars} when
     * constructing a {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to handle commands in.
     *
     * @param workerSupplier                       The {@code Executor} used to handle commands in a dedicated
     *                                             {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle}.
     * @param processingLifecycleHandlerRegistrars A vararg of {@code ProcessingLifecycleHandlerRegistrar} instance,
     *                                             used when constructing a
     *                                             {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle} to
     *                                             handle commands in.
     */
    public SimpleCommandBus(@Nonnull Executor workerSupplier,
                            @Nonnull Collection<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars) {
        this.worker = requireNonNull(workerSupplier, "The given Executor cannot be null.");
        this.processingLifecycleHandlerRegistrars = requireNonNull(processingLifecycleHandlerRegistrars).isEmpty()
                ? emptyList()
                : new ArrayList<>(processingLifecycleHandlerRegistrars);
    }

    /**
     * @throws DuplicateCommandHandlerSubscriptionException when a subscription already exists for the given
     *                                                      {@code name}.
     */
    @Override
    public SimpleCommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        CommandHandler handler = requireNonNull(commandHandler, "Given command handler cannot be null.");
        logger.debug("Subscribing command with name [{}].", name);
        var existingHandler = subscriptions.putIfAbsent(name, handler);

        if (existingHandler != null && existingHandler != handler) {
            throw new DuplicateCommandHandlerSubscriptionException(name, existingHandler, handler);
        }
        return this;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        return findCommandHandlerFor(command)
                .map(handler -> handle(command, handler))
                .orElseGet(() -> CompletableFuture.failedFuture(new NoHandlerForCommandException(format(
                        "No handler was subscribed for command [%s].", command.type()
                ))));
    }

    private Optional<CommandHandler> findCommandHandlerFor(CommandMessage<?> command) {
        return Optional.ofNullable(subscriptions.get(command.type().qualifiedName()));
    }

    /**
     * Performs the actual handling logic.
     *
     * @param command The actual command to handle.
     * @param handler The handler that must be invoked for this command.
     */
    protected CompletableFuture<? extends Message<?>> handle(CommandMessage<?> command, CommandHandler handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling command [{} ({})]", command.getIdentifier(), command.getCommandName());
        }

        AsyncUnitOfWork unitOfWork = new AsyncUnitOfWork(command.getIdentifier(), worker);
        processingLifecycleHandlerRegistrars.forEach(it -> it.registerHandlers(unitOfWork));

        var result = unitOfWork.executeWithResult(c -> handler.handle(command, c).first().asCompletableFuture());
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
        return result.thenApply(e -> e == null ? null : e.message());
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("lifecycleRegistrars", processingLifecycleHandlerRegistrars);
        descriptor.describeProperty("worker", worker);
        descriptor.describeProperty("subscriptions", subscriptions);
    }
}
