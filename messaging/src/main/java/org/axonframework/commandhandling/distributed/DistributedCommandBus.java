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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DefaultCommandBusSpanFactory;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyCommandNameFilter;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Distributed;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link CommandBus} that is aware of multiple instances of a CommandBus working together to spread
 * load. Each "physical" CommandBus instance is considered a "segment" of a conceptual distributed CommandBus.
 * <p/>
 * The DistributedCommandBus relies on a {@link CommandBusConnector} to dispatch commands and replies to different
 * segments of the CommandBus. Depending on the implementation used, each segment may run in a different JVM.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DistributedCommandBus implements CommandBus, Distributed<CommandBus>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * The initial load factor of this node when it is registered with the {@link CommandRouter}.
     */
    public static final int INITIAL_LOAD_FACTOR = 100;

    private static final String DISPATCH_ERROR_MESSAGE = "An error occurred while trying to dispatch a command "
            + "on the DistributedCommandBus";

    private final CommandRouter commandRouter;
    private final CommandBusConnector connector;
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;

    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final AtomicReference<CommandMessageFilter> commandFilter = new AtomicReference<>(DenyAll.INSTANCE);
    private final CommandBusSpanFactory spanFactory;

    private volatile int loadFactor = INITIAL_LOAD_FACTOR;

    /**
     * Instantiate a Builder to be able to create a {@link DistributedCommandBus}.
     * <p>
     * The {@link CommandCallback} is defaulted to a {@link LoggingCallback}. The {@link MessageMonitor} is defaulted to
     * a {@link NoOpMessageMonitor}. The {@link CommandBusSpanFactory} is defaulted to a
     * {@link DefaultCommandBusSpanFactory} backed by a {@link NoOpSpanFactory}. The {@link CommandRouter} and
     * {@link CommandBusConnector} are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DistributedCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link DistributedCommandBus} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link CommandRouter}, {@link CommandBusConnector} and {@link MessageMonitor} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DistributedCommandBus} instance
     */
    protected DistributedCommandBus(Builder builder) {
        builder.validate();
        this.commandRouter = builder.commandRouter;
        this.connector = builder.connector;
        this.messageMonitor = builder.messageMonitor;
        this.spanFactory = builder.spanFactory;
    }

    /**
     * Disconnect the command bus for receiving new commands, by unsubscribing all registered command handlers. This
     * shutdown operation is performed in the {@link Phase#INBOUND_COMMAND_CONNECTOR} phase.
     */
    public void disconnect() {
        commandRouter.updateMembership(loadFactor, DenyAll.INSTANCE);
    }

    /**
     * Shutdown the command bus asynchronously for dispatching commands to other instances. This process will wait for
     * dispatched commands which have not received a response yet. This shutdown operation is performed in the
     * {@link Phase#OUTBOUND_COMMAND_CONNECTORS} phase.
     *
     * @return a completable future which is resolved once all command dispatching activities are completed
     */
    public CompletableFuture<Void> shutdownDispatching() {
        return connector.initiateShutdown();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry handle) {
        handle.onShutdown(Phase.INBOUND_COMMAND_CONNECTOR, this::disconnect);
        handle.onShutdown(Phase.OUTBOUND_COMMAND_CONNECTORS, this::shutdownDispatching);
    }

    @Override
    public <C, R> CompletableFuture<CommandResultMessage<R>> dispatch(@Nonnull CommandMessage<C> command,
                                                                      @Nullable ProcessingContext processingContext) {
        logger.debug("Dispatch command [{}] with callback", command.getCommandName());

        CommandMessage<? extends C> interceptedCommand = intercept(command);
        MessageMonitor.MonitorCallback messageMonitorCallback = messageMonitor.onMessageIngested(interceptedCommand);
        Optional<Member> optionalDestination = commandRouter.findDestination(interceptedCommand);
        Span span = spanFactory.createDispatchCommandSpan(command, true).start();
        CompletableFuture<CommandResultMessage<R>> result = new CompletableFuture<CommandResultMessage<R>>()
                .whenComplete((r, e) -> {
                    if (e == null) {
                        messageMonitorCallback.reportSuccess();
                    } else {
                        messageMonitorCallback.reportFailure(e);
                    }
                });
        try (SpanScope ignored = span.makeCurrent()) {
            if (optionalDestination.isPresent()) {
                Member destination = optionalDestination.get();

                connector.send(destination,
                               spanFactory.propagateContext(interceptedCommand),
                               (c, r) -> result.complete(GenericCommandResultMessage.asCommandResultMessage(r)));
            } else {
                throw new NoHandlerForCommandException(
                        format("No node known to accept command [%s].", interceptedCommand.getCommandName())
                );
            }
        } catch (Exception e) {
            span.recordException(e);
            optionalDestination.ifPresent(Member::suspect);
            result.completeExceptionally(e);
        } finally {
            span.end();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <C> CommandMessage<? extends C> intercept(CommandMessage<C> command) {
        CommandMessage<? extends C> interceptedCommand = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            interceptedCommand = (CommandMessage<? extends C>) interceptor.handle(interceptedCommand);
        }
        return interceptedCommand;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In the DistributedCommandBus, the handler is subscribed to the local segment only.
     */
    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        logger.debug("Subscribing command with name [{}] to this distributed CommandBus. Expect similar logging on the local segment.", commandName);
        Registration reg = connector.subscribe(commandName, handler);
        updateFilter(commandFilter.get().or(new CommandNameFilter(commandName)));

        return () -> {
            updateFilter(commandFilter.get().and(new DenyCommandNameFilter(commandName)));
            return reg.cancel();
        };
    }

    private void updateFilter(CommandMessageFilter newFilter) {
        if (!commandFilter.getAndSet(newFilter).equals(newFilter)) {
            commandRouter.updateMembership(loadFactor, newFilter);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Will call {@link CommandBusConnector#localSegment()}. If this returns an {@link Optional#empty()}, this method
     * defaults to returning {@code this} as last resort.
     */
    @Override
    public CommandBus localSegment() {
        return connector.localSegment().orElse(this);
    }

    /**
     * Returns the current load factor of this node.
     *
     * @return the current load factor
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Updates the load factor of this node compared to other nodes registered with the {@link CommandRouter}.
     *
     * @param loadFactor the new load factor of this node
     */
    public void updateLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
        commandRouter.updateMembership(loadFactor, commandFilter.get());
    }

    /**
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through the
     * interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptor The interceptors to invoke when commands are dispatched
     * @return handle to deregister the interceptor
     */
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return connector.registerHandlerInterceptor(handlerInterceptor);
    }

    /**
     * Builder class to instantiate a {@link DistributedCommandBus}.
     * <p>
     * The {@link CommandCallback} is defaulted to a {@link LoggingCallback}. The {@link MessageMonitor} is defaulted to
     * a {@link NoOpMessageMonitor}. The {@link CommandBusSpanFactory} is defaulted to a
     * {@link DefaultCommandBusSpanFactory} backed by a {@link NoOpSpanFactory}. The {@link CommandRouter} and
     * {@link CommandBusConnector} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private CommandCallback<Object, Object> defaultCommandCallback = LoggingCallback.INSTANCE;
        private CommandRouter commandRouter;
        private CommandBusConnector connector;
        private MessageMonitor<? super CommandMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private CommandBusSpanFactory spanFactory = DefaultCommandBusSpanFactory
                .builder().spanFactory(NoOpSpanFactory.INSTANCE).build();

        /**
         * Sets the {@link CommandRouter} used to determine the target node for each dispatched command.
         *
         * @param commandRouter a {@link CommandRouter} used to determine the target node for each dispatched command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder commandRouter(CommandRouter commandRouter) {
            assertNonNull(commandRouter, "CommandRouter may not be null");
            this.commandRouter = commandRouter;
            return this;
        }

        /**
         * Sets the {@link CommandBusConnector} which performs the actual transport of the message to the destination
         * node.
         *
         * @param connector a {@link CommandBusConnector} which performs the actual transport of the message to the
         *                  destination node
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder connector(CommandBusConnector connector) {
            assertNonNull(connector, "CommandBusConnector may not be null");
            this.connector = connector;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} for generic types implementing {@link CommandMessage}, which is used to
         * monitor incoming messages and their execution result.
         *
         * @param messageMonitor a {@link MessageMonitor} used to monitor incoming messages and their execution result
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(MessageMonitor<? super CommandMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the callback to use when commands are dispatched in a "fire and forget" method, such as
         * {@link CommandBus#dispatch(CommandMessage, ProcessingContext)}. Defaults to using a logging callback, which requests the connectors to use
         * a fire-and-forget strategy for dispatching event.
         *
         * @param defaultCommandCallback the callback to invoke when no explicit callback is provided for a command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultCommandCallback(CommandCallback<Object, Object> defaultCommandCallback) {
            assertNonNull(defaultCommandCallback, "CommandCallback may not be null");
            this.defaultCommandCallback = defaultCommandCallback;
            return this;
        }

        /**
         * Sets the {@link CommandBusSpanFactory} implementation to use for providing tracing capabilities. Defaults to
         * a {@link DefaultCommandBusSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides no
         * tracing capabilities.
         *
         * @param spanFactory The {@link CommandBusSpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull CommandBusSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Initializes a {@link DistributedCommandBus} as specified through this Builder.
         *
         * @return a {@link DistributedCommandBus} as specified through this Builder
         */
        public DistributedCommandBus build() {
            return new DistributedCommandBus(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(commandRouter, "The CommandRouter is a hard requirement and should be provided");
            assertNonNull(connector, "The CommandBusConnector is a hard requirement and should be provided");
        }
    }
}
