/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling.distributed;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.commandhandling.distributed.PayloadConvertingCommandBusConnector;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.conversion.Converter;
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
    private PayloadConvertingCommandBusConnector testSubject;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(CommandBusConnector.class);
        mockConverter = mock(Converter.class);
        testSubject = new PayloadConvertingCommandBusConnector(
                mockDelegate, new DelegatingMessageConverter(mockConverter), byte[].class
        );
    }

    @Test
    void constructorRequiresNonNullDelegate() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingCommandBusConnector(
                null, new DelegatingMessageConverter(mockConverter), byte[].class
        ));
    }

    @Test
    void constructorRequiresNonNullConverter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new PayloadConvertingCommandBusConnector(mockDelegate, null, byte[].class));
    }

    @Test
    void constructorRequiresNonNullTargetType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingCommandBusConnector(
                mockDelegate, new DelegatingMessageConverter(mockConverter), null
        ));
    }

    @Test
    void dispatchConvertsCommandPayloadBeforeDelegating() {
        // Given
        CommandMessage originalCommand = new GenericCommandMessage(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        ProcessingContext context = mock(ProcessingContext.class);

        when(mockConverter.convert(ORIGINAL_PAYLOAD, (Type) byte[].class)).thenReturn(CONVERTED_PAYLOAD);
        when(mockDelegate.dispatch(any(CommandMessage.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture(mock(CommandResultMessage.class)));

        // When
        CompletableFuture<CommandResultMessage> result = testSubject.dispatch(originalCommand, context);

        // Then
        assertNotNull(result);
        ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockDelegate).dispatch(commandCaptor.capture(), eq(context));

        CommandMessage capturedCommand = commandCaptor.getValue();
        assertArrayEquals(CONVERTED_PAYLOAD, (byte[]) capturedCommand.payload());
        assertEquals(originalCommand.type(), capturedCommand.type());
        assertEquals(originalCommand.metadata(), capturedCommand.metadata());
    }

    @Test
    void convertingCallbackConvertsSuccessResultMessage() {
        // Given
        CommandBusConnector.Handler originalHandler = mock(CommandBusConnector.Handler.class);
        testSubject.onIncomingCommand(originalHandler);

        ArgumentCaptor<CommandBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.Handler.class);
        verify(mockDelegate).onIncomingCommand(handlerCaptor.capture());

        CommandMessage testCommand = new GenericCommandMessage(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        CommandBusConnector.ResultCallback originalCallback = mock(CommandBusConnector.ResultCallback.class);

        handlerCaptor.getValue().handle(testCommand, originalCallback);

        ArgumentCaptor<CommandBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.ResultCallback.class);
        verify(originalHandler).handle(eq(testCommand), callbackCaptor.capture());

        CommandBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        CommandResultMessage resultMessage = new GenericCommandResultMessage(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        when(mockConverter.convert(ORIGINAL_PAYLOAD, (Type) byte[].class)).thenReturn(CONVERTED_PAYLOAD);

        convertingCallback.onSuccess(resultMessage);

        // Then
        ArgumentCaptor<CommandResultMessage> messageCaptor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(originalCallback).onSuccess(messageCaptor.capture());

        Message convertedMessage = messageCaptor.getValue();
        assertArrayEquals(CONVERTED_PAYLOAD, (byte[]) convertedMessage.payload());
        assertEquals(resultMessage.type(), convertedMessage.type());
        assertEquals(resultMessage.metadata(), convertedMessage.metadata());
    }

    @Test
    void convertingCallbackHandlesNullResultMessage() {
        // Given
        CommandBusConnector.Handler originalHandler = mock(CommandBusConnector.Handler.class);
        testSubject.onIncomingCommand(originalHandler);

        ArgumentCaptor<CommandBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.Handler.class);
        verify(mockDelegate).onIncomingCommand(handlerCaptor.capture());

        CommandMessage testCommand = new GenericCommandMessage(COMMAND_TYPE, ORIGINAL_PAYLOAD);
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

        CommandMessage testCommand = new GenericCommandMessage(COMMAND_TYPE, ORIGINAL_PAYLOAD);
        CommandBusConnector.ResultCallback originalCallback = mock(CommandBusConnector.ResultCallback.class);

        handlerCaptor.getValue().handle(testCommand, originalCallback);

        ArgumentCaptor<CommandBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(CommandBusConnector.ResultCallback.class);
        verify(originalHandler).handle(eq(testCommand), callbackCaptor.capture());

        CommandBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        CommandResultMessage resultMessageWithNullPayload = new GenericCommandResultMessage(COMMAND_TYPE, null);
        convertingCallback.onSuccess(resultMessageWithNullPayload);

        // Then
        verify(originalCallback).onSuccess(resultMessageWithNullPayload);
        verify(mockConverter, never()).convert(any(), any());
    }

    @Test
    void preservesCommandMetadataWhenConverting() {
        // Given
        Metadata originalMetadata = Metadata.with("key", "value");
        CommandMessage originalCommand = new GenericCommandMessage(
                COMMAND_TYPE, ORIGINAL_PAYLOAD, originalMetadata);

        when(mockConverter.convert(ORIGINAL_PAYLOAD, byte[].class)).thenReturn(CONVERTED_PAYLOAD);
        when(mockDelegate.dispatch(any(CommandMessage.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(CommandResultMessage.class)));

        // When
        testSubject.dispatch(originalCommand, null);

        // Then
        ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockDelegate).dispatch(commandCaptor.capture(), any());

        CommandMessage capturedCommand = commandCaptor.getValue();
        assertEquals(originalMetadata, capturedCommand.metadata());
    }
}