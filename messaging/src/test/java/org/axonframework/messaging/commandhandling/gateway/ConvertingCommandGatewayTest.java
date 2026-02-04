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

package org.axonframework.messaging.commandhandling.gateway;

import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.axonframework.messaging.commandhandling.gateway.ConvertingCommandGateway;
import org.axonframework.messaging.commandhandling.gateway.FutureCommandResult;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ConvertingCommandGateway}.
 */
class ConvertingCommandGatewayTest {

    private static final String HELLO_MESSAGE = "Hello, world!";
    private static final byte[] HELLO_BYTES = HELLO_MESSAGE.getBytes(StandardCharsets.UTF_8);
    private static final MessageType TEST_TYPE = new MessageType("message");

    private MessageConverter mockConverter;
    private CommandGateway mockDelegate;
    private ConvertingCommandGateway testSubject;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(CommandGateway.class);
        mockConverter = mock(MessageConverter.class);
        testSubject = new ConvertingCommandGateway(mockDelegate, mockConverter);
    }

    @Test
    void resultIsDeserializedWhenRetrievedFromCommandResult() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any(Metadata.class), any())).thenReturn(stubResult);

        when(mockConverter.convert(any(), eq((Type) byte[].class))).thenReturn(HELLO_BYTES);

        CompletableFuture<byte[]> actual = testSubject.send("Test").resultAs(byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
    }

    @Test
    void resultIsDeserializedWhenRetrievedDirectly() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any(Metadata.class), any())).thenReturn(stubResult);

        when(mockConverter.convert(any(), eq((Type) byte[].class))).thenReturn(HELLO_BYTES);

        CompletableFuture<byte[]> actual = testSubject.send("Test", byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
    }

    @Test
    void commandResultProvidesAccessToOriginalMessage() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any(Metadata.class), any())).thenReturn(stubResult);

        when(mockConverter.convert(any(), eq((Type) byte[].class))).thenReturn(HELLO_BYTES);

        var commandResult = testSubject.send("Test");
        CompletableFuture<byte[]> actual = commandResult.resultAs(byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
        assertEquals(HELLO_MESSAGE, commandResult.getResultMessage().get().payload());
    }

    @Test
    void onSuccessCallbackIsInvokedWhenFutureCompletes() {
        CompletableFuture<Message> completableFuture = new CompletableFuture<>();
        CommandResult stubResult = new FutureCommandResult(completableFuture);
        when(mockDelegate.send(any(), any(Metadata.class), any())).thenReturn(stubResult);

        when(mockConverter.convert(any(), eq((Type) byte[].class))).thenReturn(HELLO_BYTES);

        var commandResult = testSubject.send("Test");
        CompletableFuture<byte[]> actual = commandResult.resultAs(byte[].class);
        assertFalse(actual.isDone());

        AtomicBoolean invoked = new AtomicBoolean(false);

        commandResult.onSuccess(byte[].class, (p, m) -> {
            assertArrayEquals(HELLO_BYTES, p);
            assertEquals(HELLO_MESSAGE, m.payload());
            invoked.set(true);
        });

        completableFuture.complete(new GenericMessage(TEST_TYPE, HELLO_MESSAGE));

        assertTrue(actual.isDone());
        assertTrue(invoked.get());
    }

    @Test
    void resultAsReturnsNullWhenMessageIsNull() {
        CommandResult stubResult = new FutureCommandResult(FutureUtils.emptyCompletedFuture());
        when(mockDelegate.send(any(), any(Metadata.class), any())).thenReturn(stubResult);

        when(mockConverter.convert(any(), eq((Type) byte[].class))).thenReturn(HELLO_BYTES);

        CompletableFuture<byte[]> actual = testSubject.send("Test").resultAs(byte[].class);
        assertTrue(actual.isDone());
        assertNull(actual.join());
    }
}