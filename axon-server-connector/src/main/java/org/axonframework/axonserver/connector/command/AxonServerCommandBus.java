/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.util.ExecutorServiceBuilder;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.Distributed;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Axon {@link CommandBus} implementation that connects to Axon Server to submit and receive commands and command
 * responses. Delegates incoming commands to the provided {@code localSegment}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerCommandBus implements CommandBus, Distributed<CommandBus> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final long DEFAULT_PRIORITY = 0;

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final CommandBus localSegment;
    private final CommandSerializer serializer;
    private final RoutingStrategy routingStrategy;
    private final CommandPriorityCalculator priorityCalculator;
    private final CommandLoadFactorProvider loadFactorProvider;

    private final DispatchInterceptors<CommandMessage<?>> dispatchInterceptors;
    private final TargetContextResolver<? super CommandMessage<?>> targetContextResolver;
    private final CommandCallback<Object, Object> defaultCommandCallback;
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final ExecutorService executorService;

    /**
     * Instantiate a Builder to be able to create an {@link AxonServerCommandBus}.
     * <p>
     * The {@link CommandPriorityCalculator} is defaulted to {@link CommandPriorityCalculator#defaultCommandPriorityCalculator()}
     * and the {@link TargetContextResolver} defaults to a lambda returning the {@link
     * AxonServerConfiguration#getContext()} as the context. The {@link ExecutorServiceBuilder} defaults to {@link
     * ExecutorServiceBuilder#defaultCommandExecutorServiceBuilder()}. The {@link AxonServerConnectionManager}, the
     * {@link AxonServerConfiguration}, the local {@link CommandBus}, {@link Serializer} and the {@link RoutingStrategy}
     * are a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link AxonServerCommandBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AxonServerCommandBus} instance
     */
    public AxonServerCommandBus(Builder builder) {
        builder.validate();
        this.axonServerConnectionManager = builder.axonServerConnectionManager;
        AxonServerConfiguration configuration = builder.configuration;
        this.localSegment = builder.localSegment;
        this.serializer = builder.buildSerializer();
        this.routingStrategy = builder.routingStrategy;
        this.priorityCalculator = builder.priorityCalculator;
        this.defaultCommandCallback = builder.defaultCommandCallback;
        this.loadFactorProvider = builder.loadFactorProvider;
        String context = configuration.getContext();
        this.targetContextResolver = builder.targetContextResolver.orElse(m -> context);

        this.executorService = builder.executorServiceBuilder.apply(
                builder.configuration,
                new PriorityBlockingQueue<>(1000, Comparator.comparingLong(
                        r -> r instanceof CommandProcessingTask
                                ? ((CommandProcessingTask) r).getPriority()
                                : DEFAULT_PRIORITY).reversed()
                )
        );

        dispatchInterceptors = new DispatchInterceptors<>();
    }

    /**
     * Start the Axon Server {@link CommandBus} implementation.
     */
    @StartHandler(phase = Phase.INBOUND_COMMAND_CONNECTOR)
    public void start() {
        shutdownLatch.initialize();
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        dispatch(command, defaultCommandCallback);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> commandMessage,
                                CommandCallback<? super C, ? super R> commandCallback) {
        logger.debug("Dispatch command [{}] with callback", commandMessage.getCommandName());
        doDispatch(dispatchInterceptors.intercept(commandMessage), commandCallback);
    }

    private <C, R> void doDispatch(CommandMessage<C> commandMessage,
                                   CommandCallback<? super C, ? super R> commandCallback) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new commands as this bus is being shutdown");
        ShutdownLatch.ActivityHandle commandInTransit = shutdownLatch.registerActivity();
        try {
            String context = targetContextResolver.resolveContext(commandMessage);
            Command command = serializer.serialize(commandMessage,
                                                   routingStrategy.getRoutingKey(commandMessage),
                                                   priorityCalculator.determinePriority(commandMessage));

            CompletableFuture<CommandResponse> result = axonServerConnectionManager.getConnection(context)
                                                                                   .commandChannel()
                                                                                   .sendCommand(command);
            //noinspection unchecked
            result.thenApply(commandResponse -> (CommandResultMessage<R>) serializer.deserialize(commandResponse))
                  .exceptionally(GenericCommandResultMessage::asCommandResultMessage)
                  .thenAccept(r -> commandCallback.onResult(commandMessage, r))
                  .whenComplete((r, e) -> commandInTransit.end());
        } catch (Exception e) {
            commandInTransit.end();
            AxonServerCommandDispatchException dispatchException = new AxonServerCommandDispatchException(
                    ErrorCode.COMMAND_DISPATCH_ERROR.errorCode(),
                    "Exception while dispatching a command to AxonServer", e
            );
            commandCallback.onResult(
                    commandMessage, GenericCommandResultMessage.asCommandResultMessage(dispatchException)
            );
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> messageHandler) {
        logger.debug("Subscribing command with name [{}]", commandName);
        Registration localRegistration = localSegment.subscribe(commandName, messageHandler);
        io.axoniq.axonserver.connector.Registration serverRegistration =
                axonServerConnectionManager.getConnection()
                                           .commandChannel()
                                           .registerCommandHandler(
                                                   c -> {
                                                       CompletableFuture<CommandResponse> result =
                                                               new CompletableFuture<>();
                                                       executorService.submit(new CommandProcessingTask(
                                                               c, serializer, result, localSegment
                                                       ));
                                                       return result;
                                                   },
                                                   loadFactorProvider.getFor(commandName),
                                                   commandName
                                           );

        return new AxonServerRegistration(localRegistration, serverRegistration::cancel);
    }

    @Override
    public CommandBus localSegment() {
        return localSegment;
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return localSegment.registerHandlerInterceptor(handlerInterceptor);
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    /**
     * Disconnect the command bus for receiving commands from Axon Server, by unsubscribing all registered command
     * handlers. This shutdown operation is performed in the {@link Phase#INBOUND_COMMAND_CONNECTOR} phase.
     */
    @ShutdownHandler(phase = Phase.INBOUND_COMMAND_CONNECTOR)
    public CompletableFuture<Void> disconnect() {
        if (axonServerConnectionManager.isConnected(axonServerConnectionManager.getDefaultContext())) {
            return axonServerConnectionManager.getConnection().commandChannel().prepareDisconnect();
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Shutdown the command bus asynchronously for dispatching commands to Axon Server. This process will wait for
     * dispatched commands which have not received a response yet. This shutdown operation is performed in the {@link
     * Phase#OUTBOUND_COMMAND_CONNECTORS} phase.
     *
     * @return a completable future which is resolved once all command dispatching activities are completed
     */
    @ShutdownHandler(phase = Phase.OUTBOUND_COMMAND_CONNECTORS)
    public CompletableFuture<Void> shutdownDispatching() {
        return shutdownLatch.initiateShutdown();
    }

    private static class CommandProcessingTask implements Runnable {

        private final long priority;
        private final CompletableFuture<CommandResponse> result;
        private final CommandBus localSegment;
        private final Command command;
        private final CommandSerializer serializer;

        public CommandProcessingTask(Command command,
                                     CommandSerializer serializer,
                                     CompletableFuture<CommandResponse> result,
                                     CommandBus localSegment) {
            this.command = command;
            this.serializer = serializer;
            this.priority = ProcessingInstructionHelper.priority(command.getProcessingInstructionsList());
            this.result = result;
            this.localSegment = localSegment;
        }

        public long getPriority() {
            return priority;
        }

        @Override
        public void run() {
            try {
                localSegment.dispatch(
                        serializer.deserialize(command),
                        (CommandCallback<Object, Object>) (commandMessage, commandResultMessage) -> result.complete(
                                serializer.serialize(commandResultMessage, command.getMessageIdentifier())
                        )
                );
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }
    }

    /**
     * Builder class to instantiate an {@link AxonServerCommandBus}.
     * <p>
     * The {@link CommandPriorityCalculator} is defaulted to {@link CommandPriorityCalculator#defaultCommandPriorityCalculator()}
     * and the {@link TargetContextResolver} defaults to a lambda returning the {@link
     * AxonServerConfiguration#getContext()} as the context. The {@link ExecutorServiceBuilder} defaults to {@link
     * ExecutorServiceBuilder#defaultCommandExecutorServiceBuilder()}. The {@link AxonServerConnectionManager}, the
     * {@link AxonServerConfiguration}, the local {@link CommandBus}, {@link Serializer} and the {@link RoutingStrategy}
     * are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private CommandCallback<Object, Object> defaultCommandCallback = NoOpCallback.INSTANCE;
        private AxonServerConnectionManager axonServerConnectionManager;
        private AxonServerConfiguration configuration;
        private CommandBus localSegment;
        private Serializer serializer;
        private RoutingStrategy routingStrategy;
        private CommandPriorityCalculator priorityCalculator =
                CommandPriorityCalculator.defaultCommandPriorityCalculator();
        private TargetContextResolver<? super CommandMessage<?>> targetContextResolver =
                c -> configuration.getContext();
        private ExecutorServiceBuilder executorServiceBuilder =
                ExecutorServiceBuilder.defaultCommandExecutorServiceBuilder();
        private CommandLoadFactorProvider loadFactorProvider = command -> CommandLoadFactorProvider.DEFAULT_VALUE;

        /**
         * Sets the {@link AxonServerConnectionManager} used to create connections between this application and an Axon
         * Server instance.
         *
         * @param axonServerConnectionManager an {@link AxonServerConnectionManager} used to create connections between
         *                                    this application and an Axon Server instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder axonServerConnectionManager(AxonServerConnectionManager axonServerConnectionManager) {
            assertNonNull(axonServerConnectionManager, "AxonServerConnectionManager may not be null");
            this.axonServerConnectionManager = axonServerConnectionManager;
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} used to configure several components within the Axon Server Command
         * Bus, like setting the client id or the number of command handling threads used.
         *
         * @param configuration an {@link AxonServerConfiguration} used to configure several components within the Axon
         *                      Server Command Bus
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder configuration(AxonServerConfiguration configuration) {
            assertNonNull(configuration, "AxonServerConfiguration may not be null");
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the local {@link CommandBus} used to dispatch incoming commands to the local environment.
         *
         * @param localSegment a {@link CommandBus} used to dispatch incoming commands to the local environment
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder localSegment(CommandBus localSegment) {
            assertNonNull(localSegment, "Local CommandBus may not be null");
            this.localSegment = localSegment;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize incoming and outgoing commands and command results.
         *
         * @param serializer a {@link Serializer} used to de-/serialize incoming and outgoing commands and command
         *                   results
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link RoutingStrategy} used to correctly configure connections between Axon clients and Axon
         * Server.
         *
         * @param routingStrategy a {@link RoutingStrategy}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder routingStrategy(RoutingStrategy routingStrategy) {
            assertNonNull(routingStrategy, "RoutingStrategy may not be null");
            this.routingStrategy = routingStrategy;
            return this;
        }

        /**
         * Sets the callback to use when commands are dispatched in a "fire and forget" method, such as {@link
         * #dispatch(CommandMessage)}. Defaults to a {@link NoOpCallback}. Passing {@code null} will result in a {@link
         * NoOpCallback} being used.
         *
         * @param defaultCommandCallback the callback to invoke when no explicit callback is provided for a command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultCommandCallback(CommandCallback<Object, Object> defaultCommandCallback) {
            this.defaultCommandCallback = getOrDefault(defaultCommandCallback, NoOpCallback.INSTANCE);
            return this;
        }

        /**
         * Sets the {@link CommandPriorityCalculator} used to deduce the priority of an incoming command among other
         * commands, to give precedence over high(er) valued queries for example. Defaults to a {@link
         * CommandPriorityCalculator#defaultCommandPriorityCalculator()}.
         *
         * @param priorityCalculator a {@link CommandPriorityCalculator} used to deduce the priority of an incoming
         *                           command among other commands
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder priorityCalculator(CommandPriorityCalculator priorityCalculator) {
            assertNonNull(priorityCalculator, "CommandPriorityCalculator may not be null");
            this.priorityCalculator = priorityCalculator;
            return this;
        }

        /**
         * Sets the {@link TargetContextResolver} used to resolve the target (bounded) context of an ingested {@link
         * CommandMessage}. Defaults to returning the {@link AxonServerConfiguration#getContext()} on any type of
         * command message being ingested.
         *
         * @param targetContextResolver a {@link TargetContextResolver} used to resolve the target (bounded) context of
         *                              an ingested {@link CommandMessage}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetContextResolver(TargetContextResolver<? super CommandMessage<?>> targetContextResolver) {
            assertNonNull(targetContextResolver, "TargetContextResolver may not be null");
            this.targetContextResolver = targetContextResolver;
            return this;
        }

        /**
         * Sets the {@link ExecutorServiceBuilder} which builds an {@link ExecutorService} based on a given {@link
         * AxonServerConfiguration} and {@link BlockingQueue} of {@link Runnable}. This ExecutorService is used to
         * process incoming commands with. Defaults to a {@link ThreadPoolExecutor}, using the {@link
         * AxonServerConfiguration#getCommandThreads()} for the pool size, a keep-alive-time of {@code 100ms}, the given
         * BlockingQueue as the work queue and an {@link AxonThreadFactory}.
         * <p/>
         * Note that it is highly recommended to use the given BlockingQueue if you are to provide you own {@code
         * executorServiceBuilder}, as it ensure the command's priority is taken into consideration. Defaults to {@link
         * ExecutorServiceBuilder#defaultCommandExecutorServiceBuilder()}.
         *
         * @param executorServiceBuilder an {@link ExecutorServiceBuilder} used to build an {@link ExecutorService}
         *                               based on the {@link AxonServerConfiguration} and a {@link BlockingQueue}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder executorServiceBuilder(ExecutorServiceBuilder executorServiceBuilder) {
            assertNonNull(executorServiceBuilder, "ExecutorServiceBuilder may not be null");
            this.executorServiceBuilder = executorServiceBuilder;
            return this;
        }

        /**
         * Sets the {@link CommandLoadFactorProvider} which provides the load factor values for all commands this client
         * can handle. The load factor values are sent to AxonServer during command subscription. AxonServer uses these
         * values to balance the dispatching of commands among the client instances. The default implementation of
         * loadFactorProvider returns always {@link CommandLoadFactorProvider#DEFAULT_VALUE}
         *
         * @param loadFactorProvider a {@link CommandLoadFactorProvider} used to get the load factor value for each
         *                           specific command that this client can handle
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder loadFactorProvider(CommandLoadFactorProvider loadFactorProvider) {
            assertNonNull(loadFactorProvider, "CommandLoadFactorProvider may not be null");
            this.loadFactorProvider = loadFactorProvider;
            return this;
        }

        /**
         * Initializes a {@link AxonServerCommandBus} as specified through this Builder.
         *
         * @return a {@link AxonServerCommandBus} as specified through this Builder
         */
        public AxonServerCommandBus build() {
            return new AxonServerCommandBus(this);
        }

        /**
         * Build a {@link CommandSerializer} using the configured {@code serializer} and {@code configuration}.
         *
         * @return a {@link CommandSerializer} based on the configured {@code serializer} and {@code configuration}
         */
        protected CommandSerializer buildSerializer() {
            return new CommandSerializer(serializer, configuration);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(axonServerConnectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
            assertNonNull(configuration, "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(localSegment, "The Local CommandBus is a hard requirement and should be provided");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
            assertNonNull(routingStrategy, "The RoutingStrategy is a hard requirement and should be provided");
        }
    }
}
