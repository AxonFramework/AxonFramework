/*
 * Copyright (c) 2010-2024. Axon Framework
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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.Connector;
import org.axonframework.commandhandling.distributed.PriorityResolver;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.axonframework.common.ObjectUtils.getOrDefault;

public class AxonServerConnector implements Connector {

    private final CommandChannel commandChannel;
    private final AtomicReference<BiConsumer<CommandMessage<?>, ResultCallback>> incomingHandler = new AtomicReference<>();
    private final Map<String, Registration> subscriptions = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AxonServerConnector.class);

    public AxonServerConnector(CommandChannel commandChannel) {
        this.commandChannel = commandChannel;
    }

    @Override
    public CompletableFuture<Message<byte[]>> dispatch(CommandMessage<?> command,
                                                       ProcessingContext processingContext) {
        return commandChannel.sendCommand(buildCommand(command, processingContext))
                             .thenCompose(this::buildResultMessage);
    }

    private CompletableFuture<Message<byte[]>> buildResultMessage(CommandResponse commandResponse) {
        if (commandResponse.hasErrorMessage()) {
            return CompletableFuture.failedFuture(ErrorCode.getFromCode(commandResponse.getErrorCode())
                                                           .convert(commandResponse.getErrorMessage(),
                                                                    () -> commandResponse.getPayload().getData()
                                                                                         .isEmpty()
                                                                            ? null
                                                                            : commandResponse.getPayload()
                                                                                             .getData()
                                                                                             .toByteArray()));
        }

        return CompletableFuture.completedFuture(new GenericMessage<>(
                commandResponse.getMessageIdentifier(),
                new QualifiedName("axon.framework.placeholder", "generic.command.response", "5.0.0"),
                commandResponse.getPayload().getData().toByteArray(),
                convertMap(commandResponse.getMetaDataMap(), this::convertToMetaDataValue)
        ));
    }

    private <S, T> Map<String, T> convertMap(Map<String, S> source, Function<S, T> mapper) {
        Map<String, T> result = new HashMap<>();
        source.forEach((k, v) -> {
            T convertedValue = mapper.apply(v);
            if (convertedValue != null) {
                result.put(k, convertedValue);
            }
        });
        return result;
    }

    protected Object convertToMetaDataValue(MetaDataValue value) {
        return switch (value.getDataCase()) {
            case TEXT_VALUE -> value.getTextValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case BOOLEAN_VALUE -> value.getBooleanValue();
            default -> null;
        };
    }

    protected MetaDataValue convertToMetaDataValue(Object value) {
        MetaDataValue.Builder builder = MetaDataValue.newBuilder();
        if (value instanceof CharSequence) {
            builder.setTextValue(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            builder.setDoubleValue(((Number) value).doubleValue());
        } else if (value instanceof Number) {
            builder.setNumberValue(((Number) value).longValue());
        } else if (value instanceof Boolean) {
            builder.setBooleanValue((Boolean) value);
        } else {
            logger.warn("Ignoring unsupported metadata entry: {}", value);
        }

        return builder.build();
    }

    private Command buildCommand(CommandMessage<?> command, ProcessingContext processingContext) {
        Command.Builder builder = Command.newBuilder();
        if (processingContext != null) {
            if (processingContext.containsResource(RoutingStrategy.ROUTING_KEY)) {
                String routingKey = processingContext.getResource(RoutingStrategy.ROUTING_KEY);
                builder.addProcessingInstructions(createProcessingInstruction(ProcessingKey.ROUTING_KEY,
                                                                              MetaDataValue.newBuilder()
                                                                                           .setTextValue(routingKey)))
                       .build();
            }
            if (processingContext.containsResource(PriorityResolver.PRIORITY_KEY)) {
                long routingKey = processingContext.getResource(PriorityResolver.PRIORITY_KEY);
                builder.addProcessingInstructions(createProcessingInstruction(ProcessingKey.PRIORITY,
                                                                              MetaDataValue.newBuilder()
                                                                                           .setNumberValue(routingKey)))
                       .build();
            }
        }
        Object payload = command.getPayload();
        return builder
                .setMessageIdentifier(command.getIdentifier())
                .setName(command.getCommandName())
                .putAllMetaData(convertMap(command.getMetaData(), this::convertToMetaDataValue))
                .setPayload(SerializedObject.newBuilder()
                                            .setData(ByteString.copyFrom((byte[]) payload))
                                            .setType(command.getCommandName())
                                            .build())
                .build();
    }

    @Override
    public void subscribe(String commandName, int loadFactor) {
        this.subscriptions.put(commandName,
                               this.commandChannel.registerCommandHandler(this::incoming, loadFactor, commandName));
    }

    private static ProcessingInstruction.Builder createProcessingInstruction(ProcessingKey key,
                                                                             MetaDataValue.Builder value) {
        return ProcessingInstruction.newBuilder().setKey(key).setValue(value);
    }

    private CompletableFuture<CommandResponse> incoming(Command command) {
        CompletableFuture<CommandResponse> result = new CompletableFuture<>();
        BiConsumer<CommandMessage<?>, ResultCallback> handler = incomingHandler.get();
        handler.accept(convertToCommandMessage(command), new ResultCallback() {
            @Override
            public void success(Message<?> resultMessage) {
                result.complete(createResult(command, resultMessage));
            }

            @Override
            public void error(Throwable cause) {
                result.completeExceptionally(cause);
            }
        });
        return result;
    }

    private CommandMessage<?> convertToCommandMessage(Command command) {
        SerializedObject commandPayload = command.getPayload();
        return new GenericCommandMessage<>(
                new GenericMessage<>(
                        command.getMessageIdentifier(),
                        QualifiedName.fromString(command.getName()),
                        commandPayload.getData().toByteArray(),
                        convertMap(command.getMetaDataMap(), this::convertToMetaDataValue)
                ),
                command.getName()
        );
    }

    private CommandResponse createResult(Command command, Message<?> result) {
        CommandResponse.Builder responseBuilder =
                CommandResponse.newBuilder()
                               .setMessageIdentifier(
                                       getOrDefault(result.getIdentifier(), UUID.randomUUID().toString())
                               )
                               .putAllMetaData(convertMap(result.getMetaData(),
                                                          this::convertToMetaDataValue))
                               .setRequestIdentifier(command.getMessageIdentifier());

        if (result instanceof ResultMessage commandResultMessage && commandResultMessage.isExceptional()) {
            Throwable throwable = commandResultMessage.exceptionResult();
            responseBuilder.setErrorCode(ErrorCode.getCommandExecutionErrorCode(throwable).errorCode());
            responseBuilder.setErrorMessage(ExceptionSerializer.serialize("", throwable));
            Optional<Object> optionalDetails = commandResultMessage.exceptionDetails();
            if (optionalDetails.isPresent()) {
                responseBuilder.setPayload(SerializedObject.newBuilder()
                                                           .setData(ByteString.copyFrom((byte[]) optionalDetails.get())))
                               .build();
            } else {
                logger.warn("Serializing exception [{}] without details.", throwable.getClass(), throwable);
                logger.info(
                        "To share exceptional information with the recipient it is recommended to wrap the exception in a CommandExecutionException with provided details.");
            }
        } else if (result.getPayload() != null) {
            responseBuilder.setPayload(SerializedObject.newBuilder()
                                                       .setData(ByteString.copyFrom((byte[]) result.getPayload())));
        }

        return responseBuilder.build();
    }

    @Override
    public boolean unsubscribe(String commandName) {
        Registration subscription = subscriptions.remove(commandName);
        if (subscription != null) {
            subscription.cancel();
            return true;
        }
        return false;
    }

    @Override
    public void onIncomingCommand(BiConsumer<CommandMessage<?>, ResultCallback> handler) {
        this.incomingHandler.set(handler);
    }
}
