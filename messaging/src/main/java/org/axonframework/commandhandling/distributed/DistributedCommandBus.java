/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.MonitorAwareCallback;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyCommandNameFilter;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a {@link CommandBus} that is aware of multiple instances of a CommandBus working together to
 * spread load. Each "physical" CommandBus instance is considered a "segment" of a conceptual distributed CommandBus.
 * <p/>
 * The DistributedCommandBus relies on a {@link CommandBusConnector} to dispatch commands and replies to different
 * segments of the CommandBus. Depending on the implementation used, each segment may run in a different JVM.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DistributedCommandBus implements CommandBus {

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

    private volatile int loadFactor = INITIAL_LOAD_FACTOR;

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
    }

    /**
     * Instantiate a Builder to be able to create a {@link DistributedCommandBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor}.
     * The {@link CommandRouter} and {@link CommandBusConnector} are <b>hard requirements</b> and as such should be
     * provided.
     *
     * @return a Builder to be able to create a {@link DistributedCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        LoggingCallback loggingCallback = LoggingCallback.INSTANCE;
        if (NoOpMessageMonitor.INSTANCE.equals(messageMonitor)) {
            CommandMessage<? extends C> interceptedCommand = intercept(command);
            Optional<Member> optionalDestination = commandRouter.findDestination(interceptedCommand);
            if (optionalDestination.isPresent()) {
                Member destination = optionalDestination.get();
                try {
                    connector.send(destination, interceptedCommand);
                } catch (Exception e) {
                    destination.suspect();
                    loggingCallback.onResult(interceptedCommand, asCommandResultMessage(
                            new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e)
                    ));
                }
            } else {
                loggingCallback.onResult(interceptedCommand, asCommandResultMessage(new NoHandlerForCommandException(
                        format("No node known to accept [%s]", interceptedCommand.getCommandName())
                )));
            }
        } else {
            dispatch(command, loggingCallback);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        CommandMessage<? extends C> interceptedCommand = intercept(command);
        MessageMonitor.MonitorCallback messageMonitorCallback = messageMonitor.onMessageIngested(interceptedCommand);
        Optional<Member> optionalDestination = commandRouter.findDestination(interceptedCommand);
        if (optionalDestination.isPresent()) {
            Member destination = optionalDestination.get();
            try {
                connector.send(destination,
                               interceptedCommand,
                               new MonitorAwareCallback<>(callback, messageMonitorCallback));
            } catch (Exception e) {
                messageMonitorCallback.reportFailure(e);
                destination.suspect();
                callback.onResult(interceptedCommand, asCommandResultMessage(
                        new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e)
                ));
            }
        } else {
            NoHandlerForCommandException exception = new NoHandlerForCommandException(
                    format("No node known to accept [%s]", interceptedCommand.getCommandName())
            );
            messageMonitorCallback.reportFailure(exception);
            callback.onResult(interceptedCommand, asCommandResultMessage(exception));
        }
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
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
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
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through
     * the interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptor The interceptors to invoke when commands are dispatched
     * @return handle to unregister the interceptor
     */
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return connector.registerHandlerInterceptor(handlerInterceptor);
    }

    /**
     * Builder class to instantiate a {@link DistributedCommandBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor}.
     * The {@link CommandRouter} and {@link CommandBusConnector} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private CommandRouter commandRouter;
        private CommandBusConnector connector;
        private MessageMonitor<? super CommandMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;

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
