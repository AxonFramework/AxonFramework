/*
 * Copyright (c) 2010-2018. Axon Framework
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
import io.axoniq.axonserver.grpc.command.*;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;


/**
 * Axon CommandBus implementation that connects to AxonServer to submit and receive commands.
 * @author Marc Gathier
 */
public class AxonServerCommandBus implements CommandBus {
    private final CommandBus localSegment;
    private final CommandRouterSubscriber commandRouterSubscriber;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final RoutingStrategy routingStrategy;
    private final CommandPriorityCalculator priorityCalculator;
    private final CommandSerializer serializer;
    private final AxonServerConfiguration configuration;
    private final ClientInterceptor[] interceptors;
    private final DispatchInterceptors<CommandMessage<?>> dispatchInterceptors = new DispatchInterceptors<>();
    private Logger logger = LoggerFactory.getLogger(AxonServerCommandBus.class);

    public AxonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager, AxonServerConfiguration configuration,
                                CommandBus localSegment, Serializer serializer, RoutingStrategy routingStrategy) {
        this(axonServerConnectionManager, configuration, localSegment, serializer, routingStrategy, new CommandPriorityCalculator(){});
    }
    /**
     * @param axonServerConnectionManager creates connection to AxonServer platform
     * @param configuration contains client and component names used to identify the application in AxonServer
     * @param localSegment handles incoming commands
     * @param serializer serializer/deserializer for command requests and responses
     * @param routingStrategy determines routing key based on command message
     * @param priorityCalculator calculates the request priority based on the content and adds it to the request
     */
    public AxonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager, AxonServerConfiguration configuration,
                                CommandBus localSegment, Serializer serializer, RoutingStrategy routingStrategy, CommandPriorityCalculator priorityCalculator) {
        this.localSegment = localSegment;
        this.serializer = new CommandSerializer(serializer, configuration);
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.routingStrategy = routingStrategy;
        this.priorityCalculator = priorityCalculator;
        this.configuration = configuration;
        this.commandRouterSubscriber = new CommandRouterSubscriber();
        interceptors = new ClientInterceptor[]{ new TokenAddingInterceptor(configuration.getToken()),
                new ContextAddingInterceptor(configuration.getContext())};
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        dispatch(command, (commandMessage, commandResultMessage) -> { });
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> commandMessage,
                                CommandCallback<? super C, ? super R> commandCallback) {
        logger.debug("Dispatch with callback: {}", commandMessage.getCommandName());
        CommandMessage<C> command = dispatchInterceptors.intercept(commandMessage);
        AtomicBoolean serverResponded = new AtomicBoolean(false);
        try {
            CommandServiceGrpc.newStub(axonServerConnectionManager.getChannel())
                              .withInterceptors(interceptors)
                              .dispatch(serializer.serialize(command,
                                                             routingStrategy.getRoutingKey(command),
                                                             priorityCalculator.determinePriority(command)),
                                        new StreamObserver<CommandResponse>() {
                                            @Override
                                            public void onNext(CommandResponse commandResponse) {
                                                serverResponded.set(true);
                                                if (!commandResponse.hasErrorMessage()) {
                                                    logger.debug("response received - {}", commandResponse);
                                                    try {
                                                        //noinspection unchecked
                                                        GenericCommandResultMessage<R> resultMessage = serializer
                                                                .deserialize(commandResponse);
                                                        commandCallback.onResult(command, resultMessage);
                                                    } catch (Exception ex) {
                                                        commandCallback.onResult(command, asCommandResultMessage(ex));
                                                        logger.info("Failed to deserialize payload - {} - {}",
                                                                    commandResponse.getPayload().getData(),
                                                                    ex.getCause().getMessage());
                                                    }
                                                } else {
                                                    commandCallback.onResult(command, asCommandResultMessage(
                                                            new AxonServerRemoteCommandHandlingException(
                                                                    commandResponse
                                                                            .getErrorCode(),
                                                                    commandResponse
                                                                            .getErrorMessage())));
                                                }
                                            }

                                            @Override
                                            public void onError(Throwable throwable) {
                                                serverResponded.set(true);
                                                commandCallback.onResult(command,
                                                                         asCommandResultMessage(new AxonServerCommandDispatchException(
                                                                                 ErrorCode.COMMAND_DISPATCH_ERROR
                                                                                         .errorCode(),
                                                                                 ExceptionSerializer
                                                                                         .serialize(configuration
                                                                                                            .getClientId(),
                                                                                                    throwable))));
                                            }

                                            @Override
                                            public void onCompleted() {
                                                if (!serverResponded.get()) {
                                                    commandCallback.onResult(command,
                                                                             asCommandResultMessage(new AxonServerCommandDispatchException(
                                                                                     ErrorCode.COMMAND_DISPATCH_ERROR
                                                                                             .errorCode(),
                                                                                     ErrorMessage.newBuilder()
                                                                                                 .setMessage("No result from command executor")
                                                                                                 .build())));
                                                }
                                            }
                                        });
        } catch (Exception e) {
            logger.warn("There was a problem dispatching a command {}.", command, e);
            commandCallback.onResult(command,
                                     asCommandResultMessage(new AxonServerCommandDispatchException(
                                             ErrorCode.COMMAND_DISPATCH_ERROR.errorCode(),
                                             ExceptionSerializer.serialize(configuration.getClientId(), e))));
        }
    }

    @Override
    public Registration subscribe(String s, MessageHandler<? super CommandMessage<?>> messageHandler) {
        logger.debug("Subscribe: {}", s);
        commandRouterSubscriber.subscribe(s);
        return new AxonServerRegistration(localSegment.subscribe(s, messageHandler), () -> commandRouterSubscriber.unsubscribe(s));
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
        commandRouterSubscriber.disconnect();
    }

    protected class CommandRouterSubscriber {
        private final CopyOnWriteArraySet<String> subscribedCommands = new CopyOnWriteArraySet<>();
        private final PriorityBlockingQueue<Command> commandQueue;
        private final ExecutorService executor = Executors.newFixedThreadPool(configuration.getCommandThreads(),
                                                                              new AxonThreadFactory("AxonServerCommandReceiver"));
        private volatile boolean subscribing;
        private volatile boolean running = true;

        private volatile StreamObserver<CommandProviderOutbound> subscriberStreamObserver;

        CommandRouterSubscriber() {
            axonServerConnectionManager.addReconnectListener(this::resubscribe);
            axonServerConnectionManager.addDisconnectListener(this::unsubscribeAll);
            commandQueue = new PriorityBlockingQueue<>(1000, Comparator.comparingLong(c -> -priority(c.getProcessingInstructionsList())));
            IntStream.range(0, configuration.getCommandThreads()).forEach( i -> executor.submit(this::commandExecutor));
        }

        private void commandExecutor() {
            logger.debug("Starting command Executor");
            boolean interrupted = false;
            while(!interrupted && running) {
                try {
                    Command command = commandQueue.poll(1, TimeUnit.SECONDS);
                    if( command != null) {
                        logger.debug("Received command: {}", command);
                        processCommand(command);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted queryExecutor", e);
                    interrupted = true;
                }
            }
        }

        private void resubscribe() {
            if( subscribedCommands.isEmpty() || subscribing) return;

            try {
                StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver();
                subscribedCommands.forEach(command -> outboundStreamObserver.onNext(CommandProviderOutbound.newBuilder().setSubscribe(
                        CommandSubscription.newBuilder()
                                .setCommand(command)
                                .setComponentName(configuration.getComponentName())
                                .setClientId(configuration.getClientId())
                                .setMessageId(UUID.randomUUID().toString())
                                .build()
                ).build()));
            } catch (Exception ex) {
                logger.warn("Error while resubscribing - {}", ex.getMessage());
            }
        }

        public void subscribe(String command) {
            subscribing = true;
            subscribedCommands.add(command);
            try {
                StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver();
                outboundStreamObserver.onNext(CommandProviderOutbound.newBuilder().setSubscribe(
                        CommandSubscription.newBuilder()
                                .setCommand(command)
                                .setClientId(configuration.getClientId())
                                .setComponentName(configuration.getComponentName())
                                .setMessageId(UUID.randomUUID().toString())
                                .build()
                ).build());
            } catch (Exception sre) {
                logger.debug("Subscribing command {} with AxonServer failed. Will resubscribe when connection is established.", command, sre);
            } finally {
                subscribing = false;
            }
        }



        private void processCommand(Command command) {
            StreamObserver<CommandProviderOutbound> outboundStreamObserver = getSubscriberObserver();
            try {
                dispatchLocal(serializer.deserialize(command), outboundStreamObserver);
            } catch (RuntimeException throwable) {
                logger.error("Error while dispatching command {} - {}", command.getName(), throwable.getMessage(), throwable);
                CommandProviderOutbound response = CommandProviderOutbound.newBuilder().setCommandResponse(
                        CommandResponse.newBuilder()
                                       .setMessageIdentifier(UUID.randomUUID().toString())
                                       .setRequestIdentifier(command.getMessageIdentifier())
                                       .setErrorCode(ErrorCode.COMMAND_DISPATCH_ERROR.errorCode())
                                       .setErrorMessage(ExceptionSerializer.serialize(configuration.getClientId(), throwable))
                ).build();

                outboundStreamObserver.onNext(response);
            }
        }

        private synchronized StreamObserver<CommandProviderOutbound> getSubscriberObserver() {
            if (subscriberStreamObserver == null) {
                StreamObserver<CommandProviderInbound> commandsFromRoutingServer = new StreamObserver<CommandProviderInbound>() {
                    @Override
                    public void onNext(CommandProviderInbound commandToSubscriber) {
                        logger.debug("Received from server: {}", commandToSubscriber);
                        switch (commandToSubscriber.getRequestCase()) {
                            case COMMAND:
                                commandQueue.add(commandToSubscriber.getCommand());
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable ex) {
                        logger.warn("Received error from server: {}", ex.getMessage());
                        subscriberStreamObserver = null;
                        if (ex instanceof StatusRuntimeException && ((StatusRuntimeException) ex).getStatus().getCode().equals(
                                Status.UNAVAILABLE.getCode())) {
                            return;
                        }
                        resubscribe();
                    }

                    @Override
                    public void onCompleted() {
                        logger.debug("Received completed from server");
                        subscriberStreamObserver = null;
                    }
                };

                StreamObserver<CommandProviderOutbound> stream = axonServerConnectionManager.getCommandStream(commandsFromRoutingServer, interceptors);
                logger.info("Creating new subscriber");
                subscriberStreamObserver = new FlowControllingStreamObserver<>(stream,
                        configuration,
                        flowControl -> CommandProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                        t -> t.getRequestCase().equals(CommandProviderOutbound.RequestCase.COMMAND_RESPONSE)).sendInitialPermits();

            }
            return subscriberStreamObserver;
        }

        public void unsubscribe(String command) {
            subscribedCommands.remove(command);
            try {
                getSubscriberObserver().onNext(CommandProviderOutbound.newBuilder().setUnsubscribe(
                        CommandSubscription.newBuilder()
                                .setCommand(command)
                                .setClientId(configuration.getClientId())
                                .setMessageId(UUID.randomUUID().toString())
                                .build()
                ).build());
            } catch (Exception ignored) {
            }
        }

        void unsubscribeAll() {
            for (String command : subscribedCommands) {
                try {
                    getSubscriberObserver().onNext(CommandProviderOutbound.newBuilder().setUnsubscribe(
                            CommandSubscription.newBuilder()
                                    .setCommand(command)
                                    .setClientId(configuration.getClientId())
                                    .setMessageId(UUID.randomUUID().toString())
                                    .build()
                    ).build());
                } catch (Exception ignored) {
                }
            }
            subscriberStreamObserver = null;
        }

        private <C> void dispatchLocal(CommandMessage<C> command, StreamObserver<CommandProviderOutbound> responseObserver) {
            logger.debug("DispatchLocal: {}", command.getCommandName());
            localSegment.dispatch(command, (commandMessage, commandResultMessage) -> {
                if (commandResultMessage.isExceptional()) {
                    Throwable throwable = commandResultMessage.exceptionResult();
                    CommandProviderOutbound response = CommandProviderOutbound.newBuilder().setCommandResponse(
                            CommandResponse.newBuilder()
                                           .setMessageIdentifier(UUID.randomUUID().toString())
                                           .setRequestIdentifier(command.getIdentifier())
                                           .setErrorCode(ErrorCode.COMMAND_EXECUTION_ERROR.errorCode())
                                           .setErrorMessage(ExceptionSerializer.serialize(configuration.getClientId(), throwable))
                    ).build();

                    responseObserver.onNext(response);
                    logger.info("DispatchLocal: failure {} - {}", command.getCommandName(), throwable.getMessage(), throwable);
                } else {
                    logger.debug("DispatchLocal: done {}", command.getCommandName());
                    responseObserver.onNext(serializer.serialize(commandResultMessage, command.getIdentifier()));
                }
            });
        }

        public void disconnect() {
            if( subscriberStreamObserver != null) {
                subscriberStreamObserver.onCompleted();
            }
            running = false;
            executor.shutdown();
        }
    }

    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }
}
