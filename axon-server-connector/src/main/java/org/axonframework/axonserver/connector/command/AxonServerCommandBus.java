/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.command.CommandServiceGrpc;
import io.axoniq.axonserver.grpc.command.CommandSubscription;
import io.grpc.stub.StreamObserver;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import org.axonframework.axonserver.connector.util.ResubscribableStreamObserver;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;


/**
 * Axon {@link CommandBus} implementation that connects to Axon Server to submit and receive commands and command
 * responses. Delegates incoming commands to the provided {@code localSegment}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerCommandBus.class);

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration configuration;
    private final CommandBus localSegment;
    private final CommandSerializer serializer;
    private final RoutingStrategy routingStrategy;
    private final CommandPriorityCalculator priorityCalculator;

    private final CommandHandlerProvider commandHandlerProvider;
    private final DispatchInterceptors<CommandMessage<?>> dispatchInterceptors;
    private final TargetContextResolver<? super CommandMessage<?>> targetContextResolver;

    /**
     * Instantiate an Axon Server {@link CommandBus} client. Will connect to an Axon Server instance to submit and
     * receive commands and command responses. The {@link CommandPriorityCalculator} is defaulted to the
     * {@link CommandPriorityCalculator#defaultCommandPriorityCalculator()} and the {@link TargetContextResolver}
     * defaults to a lambda returning the output from {@link AxonServerConfiguration#getContext()}.
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} which creates the connection to an Axon
     *                                    Server platform
     * @param configuration               the {@link AxonServerConfiguration} containing specifics like the client and
     *                                    component names used to identify the application in Axon Server among others
     * @param localSegment                a {@link CommandBus} handling the incoming commands for the local application
     * @param serializer                  a {@link Serializer} used for de/serialization command requests and responses
     * @param routingStrategy             a {@link RoutingStrategy} defining where a given {@link CommandMessage} should
     *                                    be routed to
     */
    public AxonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager,
                                AxonServerConfiguration configuration,
                                CommandBus localSegment,
                                Serializer serializer,
                                RoutingStrategy routingStrategy) {
        this(axonServerConnectionManager, configuration, localSegment, serializer, routingStrategy,
             CommandPriorityCalculator.defaultCommandPriorityCalculator());
    }

    /**
     * Instantiate an Axon Server Command Bus client. Will connect to an Axon Server instance to submit and receive
     * commands. Allows specifying a {@link CommandPriorityCalculator} to define the priority of command message among
     * one another. The {@link TargetContextResolver} defaults to a lambda returning the
     * output from {@link AxonServerConfiguration#getContext()}.
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} which creates the connection to an Axon
     *                                    Server platform
     * @param configuration               the {@link AxonServerConfiguration} containing client and component names used
     *                                    to identify the application in Axon Server
     * @param localSegment                a {@link CommandBus} handling the incoming commands for the local application
     * @param serializer                  a {@link Serializer} used for de/serialization command requests and responses
     * @param routingStrategy             a {@link RoutingStrategy} defining where a given {@link CommandMessage} should
     *                                    be routed to
     * @param priorityCalculator          a {@link CommandPriorityCalculator} calculating the request priority based on
     *                                    the content, and adds this priority to the request
     */
    public AxonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager,
                                AxonServerConfiguration configuration,
                                CommandBus localSegment,
                                Serializer serializer,
                                RoutingStrategy routingStrategy,
                                CommandPriorityCalculator priorityCalculator) {
        this(axonServerConnectionManager, configuration, localSegment, serializer, routingStrategy, priorityCalculator,
             c -> configuration.getContext());
    }

    /**
     * Instantiate an Axon Server Command Bus client. Will connect to an Axon Server instance to submit and receive
     * commands.
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} which creates the connection to an Axon
     *                                    Server platform
     * @param configuration               the {@link AxonServerConfiguration} containing client and component names used
     *                                    to identify the application in Axon Server
     * @param localSegment                a {@link CommandBus} handling the incoming commands for the local application
     * @param serializer                  a {@link Serializer} used for de/serialization command requests and responses
     * @param routingStrategy             a {@link RoutingStrategy} defining where a given {@link CommandMessage} should
     *                                    be routed to
     * @param priorityCalculator          a {@link CommandPriorityCalculator} calculating the request priority based on
     *                                    the content, and adds this priority to the request
     * @param targetContextResolver       resolves the context a given command should be dispatched in
     */
    public AxonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager,
                                AxonServerConfiguration configuration,
                                CommandBus localSegment,
                                Serializer serializer,
                                RoutingStrategy routingStrategy,
                                CommandPriorityCalculator priorityCalculator,
                                TargetContextResolver<? super CommandMessage<?>> targetContextResolver) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.configuration = configuration;
        this.localSegment = localSegment;
        this.serializer = new CommandSerializer(serializer, configuration);
        this.routingStrategy = routingStrategy;
        this.priorityCalculator = priorityCalculator;
        String context = configuration.getContext();
        this.targetContextResolver = targetContextResolver.orElse(m -> context);

        this.commandHandlerProvider = new CommandHandlerProvider(context);

        dispatchInterceptors = new DispatchInterceptors<>();

        this.axonServerConnectionManager.addReconnectListener(context, this::resubscribe);
        this.axonServerConnectionManager.addDisconnectListener(context, this::unsubscribe);
    }

    private void resubscribe() {
        commandHandlerProvider.resubscribe();
    }

    private void unsubscribe() {
        commandHandlerProvider.unsubscribeAll();
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        dispatch(command, NoOpCallback.INSTANCE);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> commandMessage,
                                CommandCallback<? super C, ? super R> commandCallback) {
        logger.debug("Dispatch command [{}] with callback", commandMessage.getCommandName());
        doDispatch(dispatchInterceptors.intercept(commandMessage), commandCallback);
    }

    private <C, R> void doDispatch(CommandMessage<C> commandMessage,
                                   CommandCallback<? super C, ? super R> commandCallback) {
        AtomicBoolean serverResponded = new AtomicBoolean(false);
        try {
            String context = targetContextResolver.resolveContext(commandMessage);
            Command command = serializer.serialize(commandMessage,
                                                   routingStrategy.getRoutingKey(commandMessage),
                                                   priorityCalculator.determinePriority(commandMessage));

            CommandServiceGrpc
                    .newStub(axonServerConnectionManager.getChannel(context))
                    .dispatch(command,
                              new StreamObserver<CommandResponse>() {
                                  @Override
                                  public void onNext(CommandResponse commandResponse) {
                                      serverResponded.set(true);
                                      logger.debug("Received command response [{}]", commandResponse);

                                      try {
                                          CommandResultMessage<R> resultMessage =
                                                  serializer.deserialize(commandResponse);
                                          commandCallback.onResult(commandMessage, resultMessage);
                                      } catch (Exception ex) {
                                          commandCallback.onResult(commandMessage, asCommandResultMessage(ex));
                                          logger.info("Failed to deserialize payload [{}] - Cause: {}",
                                                      commandResponse.getPayload().getData(),
                                                      ex.getCause().getMessage());
                                      }
                                  }

                                  @Override
                                  public void onError(Throwable throwable) {
                                      serverResponded.set(true);
                                      commandCallback.onResult(commandMessage, asCommandResultMessage(
                                              ErrorCode.COMMAND_DISPATCH_ERROR.convert(
                                                      configuration.getClientId(), throwable
                                              )
                                      ));
                                  }

                                  @Override
                                  public void onCompleted() {
                                      if (!serverResponded.get()) {
                                          ErrorMessage errorMessage =
                                                  ErrorMessage.newBuilder()
                                                              .setMessage("No result from command executor")
                                                              .build();
                                          commandCallback.onResult(commandMessage, asCommandResultMessage(
                                                  ErrorCode.COMMAND_DISPATCH_ERROR.convert(errorMessage))
                                          );
                                      }
                                  }
                              }
                    );
        } catch (Exception e) {
            logger.debug("There was a problem dispatching command [{}].", commandMessage, e);
            commandCallback.onResult(
                    commandMessage,
                    asCommandResultMessage(ErrorCode.COMMAND_DISPATCH_ERROR.convert(configuration.getClientId(), e))
            );
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> messageHandler) {
        logger.debug("Subscribing command with name [{}]", commandName);
        commandHandlerProvider.subscribe(commandName);
        return new AxonServerRegistration(
                localSegment.subscribe(commandName, messageHandler),
                () -> commandHandlerProvider.unsubscribe(commandName)
        );
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return localSegment.registerHandlerInterceptor(handlerInterceptor);
    }

    /**
     * Disconnect the command bus from the Axon Server.
     */
    public void disconnect() {
        if (commandHandlerProvider != null) {
            commandHandlerProvider.disconnect();
        }
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    private class CommandHandlerProvider {

        private static final int COMMAND_QUEUE_CAPACITY = 1000;
        private static final int DEFAULT_PRIORITY = 0;
        private static final long THREAD_KEEP_ALIVE_TIME = 100L;

        private final String context;
        private final CopyOnWriteArraySet<String> subscribedCommands;
        private final ExecutorService commandExecutor;

        private volatile boolean subscribing;
        private volatile boolean running = true;
        private volatile StreamObserver<CommandProviderOutbound> subscriberStreamObserver;

        CommandHandlerProvider(String context) {
            this.context = context;
            subscribedCommands = new CopyOnWriteArraySet<>();
            PriorityBlockingQueue<Runnable> commandProcessQueue =
                    new PriorityBlockingQueue<>(COMMAND_QUEUE_CAPACITY, Comparator.comparingLong(
                            r -> r instanceof CommandProcessor ? ((CommandProcessor) r).getPriority() : DEFAULT_PRIORITY
                    ));
            Integer commandThreads = configuration.getCommandThreads();
            commandExecutor = new ThreadPoolExecutor(
                    commandThreads,
                    commandThreads,
                    THREAD_KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    commandProcessQueue,
                    new AxonThreadFactory("AxonServerCommandReceiver")
            );
        }

        private void resubscribe() {
            if (subscribedCommands.isEmpty() || subscribing) {
                return;
            }

            logger.info("Resubscribing Command handlers with AxonServer");
            try {
                StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver(context);
                subscribedCommands.forEach(command -> outboundStreamObserver.onNext(
                        CommandProviderOutbound.newBuilder().setSubscribe(
                                CommandSubscription.newBuilder()
                                                   .setCommand(command)
                                                   .setComponentName(configuration.getComponentName())
                                                   .setClientId(configuration.getClientId())
                                                   .setMessageId(UUID.randomUUID().toString())
                                                   .build()
                        ).build()
                ));
            } catch (Exception e) {
                logger.warn("Error while resubscribing - [{}]", e.getMessage());
            }
        }

        public void subscribe(String commandName) {
            subscribing = true;
            subscribedCommands.add(commandName);
            try {
                StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver(context);
                outboundStreamObserver.onNext(CommandProviderOutbound.newBuilder().setSubscribe(
                        CommandSubscription.newBuilder()
                                           .setCommand(commandName)
                                           .setClientId(configuration.getClientId())
                                           .setComponentName(configuration.getComponentName())
                                           .setMessageId(UUID.randomUUID().toString())
                                           .build()
                ).build());
            } catch (Exception e) {
                logger.debug("Subscribing command with name [{}] to Axon Server failed. "
                                     + "Will resubscribe when connection is established.",
                             commandName, e);
            } finally {
                subscribing = false;
            }
        }

        private void processCommand(Command command) {
            StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver(context);
            try {
                dispatchLocal(serializer.deserialize(command), outboundStreamObserver);
            } catch (RuntimeException throwable) {
                logger.error("Error while dispatching command [{}] - Cause: {}",
                             command.getName(), throwable.getMessage(), throwable);

                if (outboundStreamObserver == null) {
                    return;
                }

                CommandProviderOutbound response = CommandProviderOutbound.newBuilder().setCommandResponse(
                        CommandResponse.newBuilder()
                                       .setMessageIdentifier(UUID.randomUUID().toString())
                                       .setRequestIdentifier(command.getMessageIdentifier())
                                       .setErrorCode(ErrorCode.COMMAND_DISPATCH_ERROR.errorCode())
                                       .setErrorMessage(
                                               ExceptionSerializer.serialize(configuration.getClientId(), throwable)
                                       )
                ).build();

                outboundStreamObserver.onNext(response);
            }
        }

        private synchronized StreamObserver<CommandProviderOutbound> getSubscriberObserver(String context) {
            if (subscriberStreamObserver != null) {
                return subscriberStreamObserver;
            }

            StreamObserver<CommandProviderInbound> commandsFromRoutingServer = new StreamObserver<CommandProviderInbound>() {
                @Override
                public void onNext(CommandProviderInbound commandToSubscriber) {
                    logger.debug("Received command from server: {}", commandToSubscriber);
                    if (commandToSubscriber.getRequestCase() == CommandProviderInbound.RequestCase.COMMAND) {
                        commandExecutor.execute(new CommandProcessor(commandToSubscriber.getCommand()));
                    }
                }

                @SuppressWarnings("Duplicates")
                @Override
                public void onError(Throwable ex) {
                    logger.warn("Command Inbound Stream closed with error", ex);
                    subscriberStreamObserver = null;
                }

                @Override
                public void onCompleted() {
                    logger.info("Received completed from server.");
                    subscriberStreamObserver = null;
                }
            };

            ResubscribableStreamObserver<CommandProviderInbound> resubscribableStreamObserver =
                    new ResubscribableStreamObserver<>(commandsFromRoutingServer, t -> resubscribe());

            StreamObserver<CommandProviderOutbound> streamObserver =
                    axonServerConnectionManager.getCommandStream(context, resubscribableStreamObserver);

            logger.info("Creating new command stream subscriber");

            subscriberStreamObserver = new FlowControllingStreamObserver<>(
                    streamObserver,
                    configuration,
                    flowControl -> CommandProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                    t -> t.getRequestCase().equals(CommandProviderOutbound.RequestCase.COMMAND_RESPONSE)
            ).sendInitialPermits();
            return subscriberStreamObserver;
        }

        public void unsubscribe(String command) {
            subscribedCommands.remove(command);
            try {
                getSubscriberObserver(context).onNext(CommandProviderOutbound.newBuilder().setUnsubscribe(
                        CommandSubscription.newBuilder()
                                           .setCommand(command)
                                           .setClientId(configuration.getClientId())
                                           .setMessageId(UUID.randomUUID().toString())
                                           .build()
                ).build());
            } catch (Exception ignored) {
                // This exception is ignored
            }
        }

        private void unsubscribeAll() {
            for (String subscribedCommand : subscribedCommands) {
                try {
                    getSubscriberObserver(context).onNext(CommandProviderOutbound.newBuilder().setUnsubscribe(
                            CommandSubscription.newBuilder()
                                               .setCommand(subscribedCommand)
                                               .setClientId(configuration.getClientId())
                                               .setMessageId(UUID.randomUUID().toString())
                                               .build()
                    ).build());
                } catch (Exception ignored) {
                    // This exception is ignored
                }
            }
            subscriberStreamObserver = null;
        }

        private <C> void dispatchLocal(CommandMessage<C> command,
                                       StreamObserver<CommandProviderOutbound> responseObserver) {
            logger.debug("Dispatch command [{}] locally", command.getCommandName());

            localSegment.dispatch(command, (commandMessage, commandResultMessage) -> {
                if (commandResultMessage.isExceptional()) {
                    Throwable throwable = commandResultMessage.exceptionResult();
                    CommandProviderOutbound response = CommandProviderOutbound.newBuilder().setCommandResponse(
                            CommandResponse.newBuilder()
                                           .setMessageIdentifier(UUID.randomUUID().toString())
                                           .setRequestIdentifier(command.getIdentifier())
                                           .setErrorCode(throwable instanceof ConcurrencyException
                                                                 ? ErrorCode.CONCURRENCY_EXCEPTION.errorCode()
                                                                 : ErrorCode.COMMAND_EXECUTION_ERROR.errorCode())
                                           .setErrorMessage(
                                                   ExceptionSerializer.serialize(configuration.getClientId(), throwable)
                                           )
                    ).build();

                    responseObserver.onNext(response);
                    logger.info("Failed to dispatch command [{}] locally - Cause: {}",
                                command.getCommandName(), throwable.getMessage(), throwable);
                } else {
                    logger.debug("Succeeded in dispatching command [{}] locally", command.getCommandName());
                    responseObserver.onNext(serializer.serialize(commandResultMessage, command.getIdentifier()));
                }
            });
        }

        void disconnect() {
            if (subscriberStreamObserver != null) {
                subscriberStreamObserver.onCompleted();
            }
            running = false;
            commandExecutor.shutdown();
        }

        /**
         * A {@link Runnable} implementation which is given to a {@link PriorityBlockingQueue} to be consumed by the
         * command {@link ExecutorService}, in order. The {@code priority} is retrieved from the provided
         * {@link Command} and used to priorities this {@link CommandProcessor} among others of it's kind.
         */
        private class CommandProcessor implements Runnable {

            private final long priority;
            private final Command command;

            private CommandProcessor(Command command) {
                this.priority = -priority(command.getProcessingInstructionsList());
                this.command = command;
            }

            public long getPriority() {
                return priority;
            }

            @Override
            public void run() {
                if (!running) {
                    logger.debug("Command Handler Provider has stopped running, "
                                         + "hence command [{}] will no longer be processed", command.getName());
                    return;
                }

                try {
                    logger.debug("Will process command: {}", command);
                    processCommand(command);
                } catch (RuntimeException | OutOfDirectMemoryError e) {
                    logger.warn("Command Processor had an exception when processing command [{}]", command, e);
                }
            }
        }
    }
}
