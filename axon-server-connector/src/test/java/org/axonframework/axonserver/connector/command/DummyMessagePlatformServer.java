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
import org.axonframework.axonserver.connector.PlatformService;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: marc
 */
public class DummyMessagePlatformServer {
    private final int port;
    private Server server;
    private Map<String, StreamObserver> subscriptions = new ConcurrentHashMap<>();
    private Map<String, CommandSubscription> commandSubscriptions = new ConcurrentHashMap<>();

    public DummyMessagePlatformServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new CommandHandler())
                .addService(new PlatformService(port))
                .build();
        server.start();
    }

    public void stop() {
        try {
            server.shutdownNow().awaitTermination();
        } catch (InterruptedException ignore) {
        } finally {
            subscriptions.clear();
        }
    }

    public StreamObserver subscriptions(String command) {
        return subscriptions.get(command);
    }

    public Optional<CommandSubscription> subscriptionForCommand(String command) {
        return Optional.ofNullable(commandSubscriptions.get(command));
    }

    public void simulateError(String command) {
        StreamObserver subscription = subscriptions.remove(command);
        subscription.onError(new RuntimeException());
    }

    class CommandHandler extends CommandServiceGrpc.CommandServiceImplBase {

        @Override
        public StreamObserver<CommandProviderOutbound> openStream(StreamObserver<CommandProviderInbound> responseObserver) {
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
                            subscriptions.remove(commandProviderOutbound.getUnsubscribe().getCommand(),
                                                 responseObserver);
                            break;
                        case FLOW_CONTROL:
                            break;
                        case COMMAND_RESPONSE:
                            break;
                        case REQUEST_NOT_SET:
                            break;
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
        public void dispatch(Command request, StreamObserver<CommandResponse> responseObserver) {
            String data = request.getPayload().getData().toStringUtf8();
            if(data.contains("error")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.DATAFILE_READ_ERROR.errorCode())
                                                       .setMessageIdentifier(request.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .build());
            } else if (data.contains("concurrency")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.CONCURRENCY_EXCEPTION.errorCode())
                                                       .setMessageIdentifier(request.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .build());
            } else if (data.contains("exception")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                                                       .setErrorCode(ErrorCode.COMMAND_EXECUTION_ERROR.errorCode())
                                                       .setMessageIdentifier(request.getMessageIdentifier())
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(data))
                                                       .setPayload(SerializedObject.newBuilder()
                                                                                   .setData(request.getPayload().getData())
                                                                                   .setType(String.class.getName())
                                                                                   .build())
                                                       .build());
            } else {
                responseObserver.onNext(CommandResponse.newBuilder()
                        .setMessageIdentifier(request.getMessageIdentifier())
                        .setPayload(SerializedObject.newBuilder()
                                .setData(request.getPayload().getData())
                                .setType(String.class.getName())
                                .build())
                        .build());
            }
            responseObserver.onCompleted();
        }

    }

}
