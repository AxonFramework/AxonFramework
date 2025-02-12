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

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

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
        MessageHandlingMember<TestMessageHandler> original = handlerDefinition.createHandler(TestMessageHandler.class,
                                                                                             TestMessageHandler.class.getDeclaredMethod(
                                                                                                     "handle",
                                                                                                     String.class),
                                                                                             parameterResolver).get();

        TimeoutWrappedMessageHandlingMember<TestMessageHandler> wrappedHandler = new TimeoutWrappedMessageHandlingMember<>(
                original, 300, 200, 50
        );

        assertThrows(TimeoutException.class,
                     () -> wrappedHandler.handle(GenericEventMessage.asEventMessage("my-message"),
                                                 new TestMessageHandler()));
    }

    public static class TestMessageHandler {

        @EventHandler
        public void handle(String message) throws InterruptedException {
            Thread.sleep(500);
        }
    }
}