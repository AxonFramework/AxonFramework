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
package org.axonframework.messaging.timeout;

import org.axonframework.common.ObjectUtils;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutWrappedMessageHandlingMemberTest {

    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Test
    void interruptsMessageHandlingMemberAsConfigured() throws NoSuchMethodException {
        Optional<MessageHandlingMember<TestMessageHandler>> optionalHandler = handlerDefinition.createHandler(
                TestMessageHandler.class,
                TestMessageHandler.class.getDeclaredMethod("handle", String.class),
                parameterResolver,
                TimeoutWrappedMessageHandlingMemberTest::returnTypeConverter
        );
        assertTrue(optionalHandler.isPresent());
        MessageHandlingMember<TestMessageHandler> original = optionalHandler.get();

        TimeoutWrappedMessageHandlingMember<TestMessageHandler> wrappedHandler = new TimeoutWrappedMessageHandlingMember<>(
                original, 300, 200, 50
        );

        assertThrows(
                TimeoutException.class,
                () -> wrappedHandler.handleSync(EventTestUtils.asEventMessage("my-message"), new TestMessageHandler())
        );
    }

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static MessageStream<?> returnTypeConverter(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            return MessageStream.fromFuture(future.thenApply(
                    r -> new GenericMessage<>(new MessageType(r.getClass()), r)
            ));
        }
        if (result instanceof MessageStream<?> stream) {
            return stream;
        }
        return MessageStream.just(new GenericMessage<>(new MessageType(ObjectUtils.nullSafeTypeOf(result)), result));
    }

    public static class TestMessageHandler {

        @EventHandler
        public void handle(String message) throws InterruptedException {
            Thread.sleep(500);
        }
    }
}