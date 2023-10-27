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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.command.CommandServiceGrpc;
import io.axoniq.axonserver.grpc.command.CommandSubscription;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.utils.PlatformService;
import org.axonframework.axonserver.connector.event.EventStoreImpl;
import org.axonframework.axonserver.connector.util.TcpUtil;
import org.axonframework.axonserver.connector.utils.ContextInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Minimal dummy implementation of gRPC connection to spoof a connection with for example Axon Server when testing Axon
 * Server connector components.
 *
 * @author Marc Gathier
 */
public class DummyMessagePlatformServer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final int port;
    private Server server;
    private final EventStoreImpl eventStore = new EventStoreImpl();

    private final Map<String, StreamObserver<?>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, CommandSubscription> commandSubscriptions = new ConcurrentHashMap<>();
    private final Set<String> unsubscribedCommands = new CopyOnWriteArraySet<>();

    public DummyMessagePlatformServer() {
        this(TcpUtil.findFreePort());
    }

    public DummyMessagePlatformServer(int port) {
        this.port = port;
    }

    public void stop() {
        try {
            server.shutdownNow().awaitTermination();
        } catch (InterruptedException ignore) {
        } finally {
            subscriptions.clear();
        }
    }

    public StreamObserver<?> subscriptions(String command) {
        return subscriptions.get(command);
    }

    public Optional<CommandSubscription> subscriptionForCommand(String command) {
        return Optional.ofNullable(commandSubscriptions.get(command));
    }

    public void simulateError(String command) {
        StreamObserver<?> subscription = subscriptions.remove(command);
        subscription.onError(new RuntimeException());
    }

    public EventStoreImpl eventStore() {
        return eventStore;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                              .addService(new CommandHandler())
                              .addService(eventStore)
                              .addService(new PlatformService(port))
                              .intercept(new ContextInterceptor())
                              .build();
        server.start();
    }

    @SuppressWarnings("unused")
    public int getPort() {
        return port;
    }

    public String getAddress() {
        return "localhost:" + port;
    }

    /**
     * Verify whether the given {@code command} is unsubscribed from the platform server.
     *
     * @param command the {@link String} to validate whether it's unsubscribed
     * @return {@code true} if the {@code command} was unsubscribed, {@code false} if it is not
     */
    public boolean isUnsubscribed(String command) {
        return unsubscribedCommands.contains(command);
    }

    class CommandHandler extends CommandServiceGrpc.CommandServiceImplBase {

        @Override
        public StreamObserver<CommandProviderOutbound> openStream(
                StreamObserver<CommandProviderInbound> responseObserver) {
            return new StreamObserver<CommandProviderOutbound>() {
                @Override
                public void onNext(CommandProviderOutbound commandProviderOutbound) {
                    switch (commandProviderOutbound.getRequestCase()) {
                        case SUBSCRIBE:
                            CommandSubscription subscription = commandProviderOutbound.getSubscribe();
                            String command = subscription.getCommand();
                            subscriptions.put(command, responseObserver);
                            commandSubscriptions.put(command, subscription);
                            break;
                        case UNSUBSCRIBE:
                            String commandToUnsubscribe = commandProviderOutbound.getUnsubscribe().getCommand();
                            subscriptions.remove(commandToUnsubscribe, responseObserver);
                            unsubscribedCommands.add(commandToUnsubscribe);
                            break;
                        case FLOW_CONTROL:
                        case COMMAND_RESPONSE:
                        case REQUEST_NOT_SET:
                            break;
                    }
                    String instructionId = commandProviderOutbound.getInstructionId();
                    if (!"".equals(instructionId)) {
                        responseObserver.onNext(CommandProviderInbound.newBuilder().setAck(InstructionAck.newBuilder().setInstructionId(instructionId).setSuccess(true).build()).build());
                    }
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            };
        }

        @Override
        public void dispatch(Command command, StreamObserver<CommandResponse> responseObserver) {
            String data = command.getPayload().getData().toStringUtf8();
            if (data.contains("error")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.DATAFILE_READ_ERROR.errorCode())
                                                       .setMessageIdentifier(command.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .build());
            } else if (data.contains("concurrency")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.CONCURRENCY_EXCEPTION.errorCode())
                                                       .setMessageIdentifier(command.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .build());
            } else if (data.contains("exception")) {
                SerializedObject serializedObject = SerializedObject.newBuilder()
                                                                    .setData(command.getPayload().getData())
                                                                    .setType(String.class.getName())
                                                                    .build();
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.COMMAND_EXECUTION_ERROR.errorCode())
                                                       .setMessageIdentifier(command.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .setPayload(serializedObject)
                                                       .build());
            } else if (data.contains("blocking")) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.debug("Sleep interrupted");
                }
                successfulResponse(command, responseObserver);
            } else {
                successfulResponse(command, responseObserver);
            }
            responseObserver.onCompleted();
        }

        private void successfulResponse(Command command, StreamObserver<CommandResponse> responseObserver) {
            SerializedObject serializedObject = SerializedObject.newBuilder()
                                                                .setData(command.getPayload().getData())
                                                                .setType(String.class.getName())
                                                                .build();
            responseObserver.onNext(CommandResponse.newBuilder()
                                                   .setMessageIdentifier(command.getMessageIdentifier())
                                                   .setPayload(serializedObject)
                                                   .build());
        }
    }
}
