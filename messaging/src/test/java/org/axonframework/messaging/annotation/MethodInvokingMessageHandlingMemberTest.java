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

package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.HandlerAttributes;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MethodInvokingMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class MethodInvokingMessageHandlingMemberTest {

    private MethodInvokingMessageHandlingMember<AnnotatedHandler> testSubject;

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static CompletableFuture<Object> returnTypeConverter(Object result) {
        return result instanceof CompletableFuture<?>
                ? (CompletableFuture<Object>) result
                : CompletableFuture.completedFuture(result);
    }

    @BeforeEach
    void setUp() {
        testSubject = new MethodInvokingMessageHandlingMember<>(
                AnnotatedHandler.class.getMethods()[0],
                EventMessage.class,
                String.class,
                ClasspathParameterResolverFactory.forClass(AnnotatedHandler.class),
                MethodInvokingMessageHandlingMemberTest::returnTypeConverter
        );
    }

    @Test
    void canHandleMessageType() {
        assertTrue(testSubject.canHandleMessageType(EventMessage.class));
        assertFalse(testSubject.canHandleMessageType(CommandMessage.class));
    }

    @Test
    void attributeReturnsNonEmptyOptionalForMatchingAttributeKey() {
        Optional<Object> resultMessageType = testSubject.attribute(HandlerAttributes.MESSAGE_TYPE);
        Optional<Object> resultPayloadType = testSubject.attribute(HandlerAttributes.PAYLOAD_TYPE);

        assertTrue(resultMessageType.isPresent());
        assertEquals(EventMessage.class, resultMessageType.get());

        assertTrue(resultPayloadType.isPresent());
        assertEquals(Object.class, resultPayloadType.get());
    }

    @SuppressWarnings("unused")
    private static class AnnotatedHandler {

        @EventHandler
        public void handlingMethod(String event) {

        }
    }
}