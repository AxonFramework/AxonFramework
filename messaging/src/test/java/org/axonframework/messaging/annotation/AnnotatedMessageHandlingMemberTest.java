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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.HandlerAttributes;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotatedMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class AnnotatedMessageHandlingMemberTest {

    private AnnotatedMessageHandlingMember<AnnotatedHandler> testSubject;

    @BeforeEach
    void setUp() {
        try {
        testSubject = new AnnotatedMessageHandlingMember<>(
                AnnotatedHandler.class.getMethod("handlingMethod", String.class),
                EventMessage.class,
                String.class,
                ClasspathParameterResolverFactory.forClass(AnnotatedHandler.class)
        ); }
        catch (NoSuchMethodException e){
            fail(e.getMessage());
        }
    }

    @Test
    void canHandleMessageType() {
        assertTrue(testSubject.canHandleMessageType(EventMessage.class));
        assertFalse(testSubject.canHandleMessageType(CommandMessage.class));
    }

    @Test
    void hasAnnotation() {
        assertTrue(testSubject.hasAnnotation(EventHandler.class));
        assertFalse(testSubject.hasAnnotation(CommandHandler.class));
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