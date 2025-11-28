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

package org.axonframework.axonserver.connector.command;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonServerCommandBusConnector}.
 *
 * @author Jens Mayer
 */
class AxonServerCommandBusConnectorTest {

    private static final String TEST_CLIENT_ID = "my-client-id";
    private static final String TEST_COMPONENT_NAME = "my-component-name";
    private static final QualifiedName ANY_TEST_COMMAND_NAME = new QualifiedName("TestCommand");
    private static final String ANY_TEST_MESSAGE_ID = "test-message-id";
    private static final String ANY_TEST_COMMAND_TYPE = "TestCommandType";
    private static final String ANY_TEST_REVISION = "1.0";
    private static final MessageType ANY_TEST_TYPE = new MessageType(ANY_TEST_COMMAND_TYPE, ANY_TEST_REVISION);
    private static final byte[] ANY_TEST_PAYLOAD = "test-payload".getBytes();
    private static final int ANY_TEST_LOAD_FACTOR = 100;
    private static final int ANY_TEST_PRIORITY = 5;
    private static final String ANY_TEST_ROUTING_KEY = "test-routing-key";

    private AxonServerConnection connection;
    private CommandChannel commandChannel;

    private AxonServerCommandBusConnector testSubject;

    @BeforeEach
    void setUp() {
        connection = mock(AxonServerConnection.class);
        commandChannel = mock(CommandChannel.class);
        when(connection.commandChannel()).thenReturn(commandChannel);

        AxonServerConfiguration serverConfig = new AxonServerConfiguration();
        serverConfig.setClientId(TEST_CLIENT_ID);
        serverConfig.setComponentName(TEST_COMPONENT_NAME);
        testSubject = new AxonServerCommandBusConnector(connection, serverConfig);
    }

    @Test
    void constructionWithConnectionNullRefFails() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new AxonServerCommandBusConnector(null, new AxonServerConfiguration()));
    }

    @Test
    void constructionWithAxonServerConfigurationNullRefFails() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new AxonServerCommandBusConnector(connection, null));
    }

    @Test
    void dispatchingCommandMessageWithInvalidPayloadTypeFails() {
        CommandMessage command = new GenericCommandMessage(
                new GenericMessage(ANY_TEST_MESSAGE_ID, ANY_TEST_TYPE, "invalid-payload", new HashMap<>())
        );
        assertThrows(IllegalArgumentException.class, () -> testSubject.dispatch(command, null));
    }

    @Test
    void dispatchingCommandMessageWithValidPayloadResultsToResponse() {
        // Arrange
        CommandMessage command = createTestCommandMessage();
        CommandResponse response = createSuccessfulCommandResponse();
        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        when(commandChannel.sendCommand(commandCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(response));

        // Act
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

        // Assert
        Command dispatchedCommand = commandCaptor.getValue();
        assertThat(dispatchedCommand.getClientId()).isEqualTo(TEST_CLIENT_ID);
        assertThat(dispatchedCommand.getComponentName()).isEqualTo(TEST_COMPONENT_NAME);
        assertDoesNotThrow(() -> result.get());
        verify(commandChannel).sendCommand(any(Command.class));
    }

    @Test
    void dispatchingBuildsCorrectOutgoingCommand() {
        // Arrange
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        CommandMessage command = new GenericCommandMessage(
                new GenericMessage(ANY_TEST_MESSAGE_ID, ANY_TEST_TYPE, ANY_TEST_PAYLOAD, metadata),
                ANY_TEST_ROUTING_KEY,
                ANY_TEST_PRIORITY
        );

        when(commandChannel.sendCommand(any(Command.class)))
                .thenReturn(CompletableFuture.completedFuture(createSuccessfulCommandResponse()));

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);

        // Act
        testSubject.dispatch(command, null);

        // Assert
        verify(commandChannel).sendCommand(commandCaptor.capture());
        Command sentCommand = commandCaptor.getValue();

        assertEquals(ANY_TEST_MESSAGE_ID, sentCommand.getMessageIdentifier());
        assertEquals(ANY_TEST_COMMAND_TYPE, sentCommand.getName());
        assertEquals(ANY_TEST_COMMAND_TYPE, sentCommand.getPayload().getType());
        assertEquals(ANY_TEST_REVISION, sentCommand.getPayload().getRevision());
        assertArrayEquals(ANY_TEST_PAYLOAD, sentCommand.getPayload().getData().toByteArray());
        assertEquals(2, sentCommand.getMetaDataCount());
        assertTrue(sentCommand.getProcessingInstructionsList().size() >= 2); // Priority and routing key
    }

    @Test
    void dispatchingWithEmptyPriorityDoesNotAddPriorityInstruction() {
        // Arrange
        CommandMessage command = createTestCommandMessage();

        when(commandChannel.sendCommand(any(Command.class)))
                .thenReturn(CompletableFuture.completedFuture(createSuccessfulCommandResponse()));

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);

        // Act
        testSubject.dispatch(command, null);

        // Assert
        verify(commandChannel).sendCommand(commandCaptor.capture());
        Command sentCommand = commandCaptor.getValue();

        assertFalse(sentCommand.getProcessingInstructionsList().stream()
                               .anyMatch(pi -> pi.getKey() == ProcessingKey.PRIORITY));
    }

    @Test
    void dispatchingHandlesErrorResponse() {
        // Arrange
        CommandMessage command = createTestCommandMessage();
        CommandResponse errorResponse = CommandResponse.newBuilder()
                                                       .setMessageIdentifier(UUID.randomUUID().toString())
                                                       .setErrorCode("COMMAND_EXECUTION_ERROR")
                                                       .setErrorMessage(ErrorMessage.newBuilder().setMessage(
                                                               "Command execution error").build()
                                                       )
                                                       .build();

        when(commandChannel.sendCommand(any(Command.class)))
                .thenReturn(CompletableFuture.completedFuture(errorResponse));

        // Act
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

        // Assert
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void dispatchingHandlesEmptyPayloadResponse() {
        // Arrange
        CommandMessage command = createTestCommandMessage();
        CommandResponse response = CommandResponse.newBuilder()
                                                  .setMessageIdentifier(UUID.randomUUID().toString())
                                                  .setPayload(SerializedObject.newBuilder()
                                                                              .setType("")
                                                                              .setRevision("")
                                                                              .setData(ByteString.EMPTY)
                                                                              .build())
                                                  .build();

        when(commandChannel.sendCommand(any(Command.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // Act
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(command, null);

        // Assert
        assertDoesNotThrow(() -> {
            CommandResultMessage resultMessage = result.get();
            assertNull(resultMessage);
        });
    }

    @Test
    void subscribeRegistersCommandHandlerWithCorrectParameters() {
        // Arrange
        Registration mockRegistration = mock(Registration.class);
        when(commandChannel.registerCommandHandler(any(), eq(ANY_TEST_LOAD_FACTOR), eq(ANY_TEST_COMMAND_NAME.name())))
                .thenReturn(mockRegistration);

        // Act
        testSubject.subscribe(ANY_TEST_COMMAND_NAME, ANY_TEST_LOAD_FACTOR);

        // Assert
        assertThat(getSubscriptions(testSubject)).containsEntry(ANY_TEST_COMMAND_NAME, mockRegistration);
    }

    @Test
    void subscribeWithNegativeLoadFactorThrowsException() {
        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.subscribe(ANY_TEST_COMMAND_NAME, -1));
    }

    @Test
    void subscribeWithAsyncRegistrationWaitsForAcknowledgment() {
        // Arrange
        Registration asyncRegistration = mock(Registration.class);
        when(commandChannel.registerCommandHandler(any(), eq(ANY_TEST_LOAD_FACTOR), eq(ANY_TEST_COMMAND_NAME.name())))
                .thenReturn(asyncRegistration);

        // Act
        testSubject.subscribe(ANY_TEST_COMMAND_NAME, ANY_TEST_LOAD_FACTOR);

        // Assert
        verify(asyncRegistration).onAck(any(Runnable.class));
    }

    @Test
    void unsubscribeRemovesAndCancelsRegistration() {
        // Arrange
        Registration mockRegistration = mock(Registration.class);
        when(commandChannel.registerCommandHandler(any(), eq(ANY_TEST_LOAD_FACTOR), eq(ANY_TEST_COMMAND_NAME.name())))
                .thenReturn(mockRegistration);

        testSubject.subscribe(ANY_TEST_COMMAND_NAME, ANY_TEST_LOAD_FACTOR);

        // Act
        boolean result = testSubject.unsubscribe(ANY_TEST_COMMAND_NAME);

        // Assert
        assertTrue(result);
        verify(mockRegistration).cancel();
        assertFalse(getSubscriptions(testSubject).containsKey(ANY_TEST_COMMAND_NAME));

        // Second unsubscribe should return false
        assertFalse(testSubject.unsubscribe(ANY_TEST_COMMAND_NAME));
    }

    @Test
    void unsubscribeNonExistentCommandReturnsFalse() {
        // Act
        boolean result = testSubject.unsubscribe(ANY_TEST_COMMAND_NAME);

        // Assert
        assertFalse(result);
    }

    @Test
    void onIncomingCommandSetsHandler() {
        // Arrange
        CommandBusConnector.Handler handler = mock(CommandBusConnector.Handler.class);

        // Act
        testSubject.onIncomingCommand(handler);

        // Assert
        assertThat(getIncomingHandler(testSubject)).isSameAs(handler);
    }

    @Test
    void disconnectInvokesPrepareDisconnectOnCommandChannel() {
        when(commandChannel.prepareDisconnect()).thenReturn(CompletableFuture.completedFuture(null));
        when(connection.isConnected()).thenReturn(true);

        testSubject.disconnect();

        verify(commandChannel).prepareDisconnect();
    }

    @Test
    void afterShutdownDispatchingAnShutdownInProgressExceptionIsThrownOnDispatchInvocation() {
        CommandMessage testCommand = new GenericCommandMessage(ANY_TEST_TYPE, ANY_TEST_PAYLOAD);

        // when...
        testSubject.shutdownDispatching();
        // then...
        assertThrows(
                ShutdownInProgressException.class,
                () -> testSubject.dispatch(testCommand, null)
        );
    }

    @Test
    void shutdownDispatchingWaitsForCommandsInTransitToComplete() {
        CommandMessage testCommand = new GenericCommandMessage(ANY_TEST_TYPE, ANY_TEST_PAYLOAD);
        CompletableFuture<CommandResponse> testResponseFuture = new CompletableFuture<>();
        AtomicBoolean handled = new AtomicBoolean(false);
        when(commandChannel.sendCommand(any())).thenReturn(testResponseFuture);

        // given ...
        testSubject.dispatch(testCommand, null)
                   .whenComplete((result, exception) -> handled.set(true));
        Thread.ofVirtual()
              .name("Return Command Response")
              .start(() -> {
                  try {
                      Thread.sleep(200);
                      testResponseFuture.complete(mock(CommandResponse.class));
                  } catch (InterruptedException e) {
                      fail(e.getMessage());
                      throw new RuntimeException(e);
                  }
              });

        // when ...
        CompletableFuture<Void> dispatchingHasShutdown = testSubject.shutdownDispatching();

        // then ... Wait on the shutdownDispatching-thread, after which the command should have been handled
        dispatchingHasShutdown.join();
        await("Dispatch completion").atMost(Duration.ofSeconds(1))
                                    .pollDelay(Duration.ofMillis(25))
                                    .untilAsserted(() -> {
                                        assertTrue(handled.get());
                                        assertTrue(dispatchingHasShutdown.isDone());
                                    });
    }

    @Nested
    class GracefulShutdown {

        @Test
        void disconnectCompletesWhenIncomingCommandsAreHandled() {
            when(connection.isConnected()).thenReturn(true);
            CompletableFuture<Void> disconnectCompletion = new CompletableFuture<>();
            when(commandChannel.prepareDisconnect()).thenReturn(disconnectCompletion);

            AtomicReference<CommandBusConnector.ResultCallback> resultCallback = new AtomicReference<>();
            testSubject.onIncomingCommand((commandMessage, callback) -> resultCallback.set(callback));

            getIncomingHandler(testSubject).handle(createTestCommandMessage(), mock());

            assertNotNull(resultCallback.get(), "Command was not received");
            CompletableFuture<Void> result = testSubject.disconnect();
            assertNotNull(result);
            verify(commandChannel).prepareDisconnect();
            assertFalse(result.isDone());

            disconnectCompletion.complete(null);

            resultCallback.get().onSuccess(new GenericCommandResultMessage(ANY_TEST_TYPE, ANY_TEST_PAYLOAD));
            assertTrue(result.isDone());
        }

        @Test
        void disconnectCompletesOnPrepareWhenNoActiveCommandsAvailable() {
            when(connection.isConnected()).thenReturn(true);
            CompletableFuture<Void> disconnectCompletion = new CompletableFuture<>();
            when(commandChannel.prepareDisconnect()).thenReturn(disconnectCompletion);

            CompletableFuture<Void> result = testSubject.disconnect();
            assertNotNull(result);
            verify(commandChannel).prepareDisconnect();
            assertFalse(result.isDone());

            disconnectCompletion.complete(null);

            assertTrue(result.isDone());
        }
    }

    // Helpers
    private CommandMessage createTestCommandMessage() {
        return new GenericCommandMessage(new GenericMessage(ANY_TEST_MESSAGE_ID,
                                                            ANY_TEST_TYPE,
                                                            ANY_TEST_PAYLOAD,
                                                            new HashMap<>()));
    }

    private CommandResponse createSuccessfulCommandResponse() {
        return CommandResponse.newBuilder()
                              .setMessageIdentifier(UUID.randomUUID().toString())
                              .setPayload(SerializedObject.newBuilder()
                                                          .setType("ResponseType")
                                                          .setRevision("1.0")
                                                          .setData(ByteString.copyFrom("response-payload".getBytes()))
                                                          .build())
                              .putMetaData("responseKey",
                                           MetaDataValue.newBuilder().setTextValue("responseValue").build())
                              .build();
    }

    private Map<QualifiedName, Registration> getSubscriptions(AxonServerCommandBusConnector instance) {
        try {
            Field field = instance.getClass().getDeclaredField("subscriptions");
            field.setAccessible(true);

            //noinspection unchecked
            return (Map<QualifiedName, Registration>) field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private CommandBusConnector.Handler getIncomingHandler(AxonServerCommandBusConnector instance) {
        try {
            Field field = instance.getClass().getDeclaredField("incomingHandler");
            field.setAccessible(true);
            return (CommandBusConnector.Handler) field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}