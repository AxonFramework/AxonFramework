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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.DelegatingMessageConverter;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageConverter;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayloadConvertingCommandBusConnectorTest {

    private static final String ORIGINAL_PAYLOAD = "original";
    private static final byte[] CONVERTED_PAYLOAD = ORIGINAL_PAYLOAD.getBytes();
    private static final MessageType COMMAND_TYPE = new MessageType("TestCommand");

    private CommandBusConnector mockDelegate;
    private Converter mockConverter;
    private PayloadConvertingCommandBusConnector<byte[]> testSubject;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(CommandBusConnector.class);
        mockConverter = mock(Converter.class);
        testSubject = new PayloadConvertingCommandBusConnector<>(
                mockDelegate, new DelegatingMessageConverter(mockConverter), byte[].class
        );
    }

    @Test
    void constructorRequiresNonNullDelegate() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingCommandBusConnector<>(
                null, new DelegatingMessageConverter(mockConverter), byte[].class
        ));
    }

    @Test
    void constructorRequiresNonNullConverter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new PayloadConvertingCommandBusConnector<>(mockDelegate, null, byte[].class));
    }

    @Test
    void constructorRequiresNonNullTargetType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingCommandBusConnector<>(
                mockDelegate, new DelegatingMessageConverter(mockConverter), null
        ));
    }

    @Test
    void dispatchConvertsCommandPayloadBeforeDelegating() {
        // Given
        CommandMessage<String> originalCommand = new GenericCommandMessage<>(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        ProcessingContext context = mock(ProcessingContext.class);

        when(mockConverter.convert(ORIGINAL_PAYLOAD, (Type) byte[].class)).thenReturn(CONVERTED_PAYLOAD);
        //noinspection unchecked
        when(mockDelegate.dispatch(any(CommandMessage.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture(mock(CommandResultMessage.class)));

        // When
        CompletableFuture<CommandResultMessage<?>> result = testSubject.dispatch(originalCommand, context);

        // Then
        assertNotNull(result);
        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockDelegate).dispatch(commandCaptor.capture(), eq(context));

        CommandMessage<?> capturedCommand = commandCaptor.getValue();
        assertArrayEquals(CONVERTED_PAYLOAD, (byte[]) capturedCommand.payload());
        assertEquals(originalCommand.type(), capturedCommand.type());
        assertEquals(originalCommand.metaData(), capturedCommand.metaData());
    }

    @Test
    void convertingCallbackConvertsSuccessResultMessage() {
        // Given
        CommandBusConnector.Handler originalHandler = mock(CommandBusConnector.Handler.class);
        testSubject.onIncomingCommand(originalHandler);

        ArgumentCaptor<CommandBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.Handler.class);
        verify(mockDelegate).onIncomingCommand(handlerCaptor.capture());

        CommandMessage<String> testCommand = new GenericCommandMessage<>(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        CommandBusConnector.ResultCallback originalCallback = mock(CommandBusConnector.ResultCallback.class);

        handlerCaptor.getValue().handle(testCommand, originalCallback);

        ArgumentCaptor<CommandBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.ResultCallback.class);
        verify(originalHandler).handle(eq(testCommand), callbackCaptor.capture());

        CommandBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        CommandResultMessage<String> resultMessage = new GenericCommandResultMessage<>(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        when(mockConverter.convert(ORIGINAL_PAYLOAD, (Type) byte[].class)).thenReturn(CONVERTED_PAYLOAD);

        convertingCallback.onSuccess(resultMessage);

        // Then
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<?>> messageCaptor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(originalCallback).onSuccess(messageCaptor.capture());

        Message<?> convertedMessage = messageCaptor.getValue();
        assertArrayEquals(CONVERTED_PAYLOAD, (byte[]) convertedMessage.payload());
        assertEquals(resultMessage.type(), convertedMessage.type());
        assertEquals(resultMessage.metaData(), convertedMessage.metaData());
    }

    @Test
    void convertingCallbackHandlesNullResultMessage() {
        // Given
        CommandBusConnector.Handler originalHandler = mock(CommandBusConnector.Handler.class);
        testSubject.onIncomingCommand(originalHandler);

        ArgumentCaptor<CommandBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.Handler.class);
        verify(mockDelegate).onIncomingCommand(handlerCaptor.capture());

        CommandMessage<String> testCommand = new GenericCommandMessage<>(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        CommandBusConnector.ResultCallback originalCallback = mock(CommandBusConnector.ResultCallback.class);

        handlerCaptor.getValue().handle(testCommand, originalCallback);

        ArgumentCaptor<CommandBusConnector.ResultCallback> callbackCaptor = ArgumentCaptor.forClass(CommandBusConnector.ResultCallback.class);
        verify(originalHandler).handle(eq(testCommand), callbackCaptor.capture());

        CommandBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        convertingCallback.onSuccess(null);

        // Then
        verify(originalCallback).onSuccess(null);
        verify(mockConverter, never()).convert(any(), any());
    }

    @Test
    void convertingCallbackHandlesMessageWithNullPayload() {
        // Given
        CommandBusConnector.Handler originalHandler = mock(CommandBusConnector.Handler.class);
        testSubject.onIncomingCommand(originalHandler);

        ArgumentCaptor<CommandBusConnector.Handler> handlerCaptor = ArgumentCaptor.forClass(CommandBusConnector.Handler.class);
        verify(mockDelegate).onIncomingCommand(handlerCaptor.capture());

        CommandMessage<String> testCommand = new GenericCommandMessage<>(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        CommandBusConnector.ResultCallback originalCallback = mock(CommandBusConnector.ResultCallback.class);

        handlerCaptor.getValue().handle(testCommand, originalCallback);

        ArgumentCaptor<CommandBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.ResultCallback.class);
        verify(originalHandler).handle(eq(testCommand), callbackCaptor.capture());

        CommandBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        CommandResultMessage<String> resultMessageWithNullPayload =
                new GenericCommandResultMessage<>(COMMAND_TYPE, null);
        convertingCallback.onSuccess(resultMessageWithNullPayload);

        // Then
        verify(originalCallback).onSuccess(resultMessageWithNullPayload);
        verify(mockConverter, never()).convert(any(), any());
    }

    @Test
    void preservesCommandMetadataWhenConverting() {
        // Given
        MetaData originalMetaData = MetaData.with("key", "value");
        CommandMessage<String> originalCommand = new GenericCommandMessage<>(
                COMMAND_TYPE, ORIGINAL_PAYLOAD, originalMetaData);

        when(mockConverter.convert(ORIGINAL_PAYLOAD, byte[].class)).thenReturn(CONVERTED_PAYLOAD);
        //noinspection unchecked
        when(mockDelegate.dispatch(any(CommandMessage.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(CommandResultMessage.class)));

        // When
        testSubject.dispatch(originalCommand, null);

        // Then
        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockDelegate).dispatch(commandCaptor.capture(), any());

        CommandMessage<?> capturedCommand = commandCaptor.getValue();
        assertEquals(originalMetaData, capturedCommand.metaData());
    }
}