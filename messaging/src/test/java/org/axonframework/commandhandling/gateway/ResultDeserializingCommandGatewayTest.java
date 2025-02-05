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

package org.axonframework.commandhandling.gateway;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultDeserializingCommandGatewayTest {

    private static final String HELLO_MESSAGE = "Hello, world!";
    private static final byte[] HELLO_BYTES = HELLO_MESSAGE.getBytes(StandardCharsets.UTF_8);
    private static final MessageType TEST_TYPE = new MessageType("message");

    private Serializer mockSerializer;
    private CommandGateway mockDelegate;
    private ResultDeserializingCommandGateway testSubject;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(CommandGateway.class);
        mockSerializer = mock(Serializer.class);
        testSubject = new ResultDeserializingCommandGateway(mockDelegate, mockSerializer);
    }

    @Test
    void testResultIsDeserializedWhenRetrievedFromCommandResult() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage<>(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any())).thenReturn(stubResult);

        when(mockSerializer.convert(any(), eq(byte[].class))).thenReturn(HELLO_BYTES);

        CompletableFuture<byte[]> actual = testSubject.send("Test", null).resultAs(byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
    }

    @Test
    void testResultIsDeserializedWhenRetrievedDirectly() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage<>(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any())).thenReturn(stubResult);

        when(mockSerializer.convert(any(), eq(byte[].class))).thenReturn(HELLO_BYTES);

        CompletableFuture<byte[]> actual = testSubject.send("Test", null, byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
    }

    @Test
    void testCommandResultProvidesAccessToOriginalMessage() throws ExecutionException, InterruptedException {
        CommandResult stubResult = new FutureCommandResult(
                CompletableFuture.completedFuture(new GenericMessage<>(TEST_TYPE, HELLO_MESSAGE))
        );
        when(mockDelegate.send(any(), any())).thenReturn(stubResult);

        when(mockSerializer.convert(any(), eq(byte[].class))).thenReturn(HELLO_BYTES);

        var commandResult = testSubject.send("Test", null);
        CompletableFuture<byte[]> actual = commandResult.resultAs(byte[].class);
        assertTrue(actual.isDone());
        assertArrayEquals(HELLO_BYTES, actual.get());
        assertEquals(HELLO_MESSAGE, commandResult.getResultMessage().get().getPayload());
    }

    @Test
    void testOnSuccessCallbackIsInvokedWhenFutureCompletes() {
        CompletableFuture<Message<Object>> completableFuture = new CompletableFuture<>();
        CommandResult stubResult = new FutureCommandResult(completableFuture);
        when(mockDelegate.send(any(), any())).thenReturn(stubResult);

        when(mockSerializer.convert(any(), eq(byte[].class))).thenReturn(HELLO_BYTES);

        var commandResult = testSubject.send("Test", null);
        CompletableFuture<byte[]> actual = commandResult.resultAs(byte[].class);
        assertFalse(actual.isDone());

        AtomicBoolean invoked = new AtomicBoolean(false);

        commandResult.onSuccess(byte[].class, (p, m) -> {
            assertArrayEquals(HELLO_BYTES, p);
            assertEquals(HELLO_MESSAGE, m.getPayload());
            invoked.set(true);
        });

        completableFuture.complete(new GenericMessage<>(TEST_TYPE, HELLO_MESSAGE));

        assertTrue(actual.isDone());
        assertTrue(invoked.get());
    }
}