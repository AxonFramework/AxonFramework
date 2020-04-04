/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MessageIdentifierParameterResolverFactoryTest {

    private MessageIdentifierParameterResolverFactory testSubject;

    private Method messageIdentifierMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new MessageIdentifierParameterResolverFactory();

        messageIdentifierMethod = getClass().getMethod("someMessageIdentifierMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someMessageIdentifierMethod(@MessageIdentifier String messageIdentifier) {
        //Used in setUp()
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someNonAnnotatedMethod(String messageIdentifier) {
        //Used in setUp()
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someIntegerMethod(@MessageIdentifier Integer messageIdentifier) {
        //Used in setUp()
    }

    @Test
    void testResolvesToMessageIdentifierWhenAnnotatedForEventMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(messageIdentifierMethod, messageIdentifierMethod.getParameters(), 0);
        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(eventMessage));
        assertEquals(eventMessage.getIdentifier(), resolver.resolveParameterValue(eventMessage));
    }

    @Test
    void testResolvesToMessageIdentifierWhenAnnotatedForCommandMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(messageIdentifierMethod, messageIdentifierMethod.getParameters(), 0);
        CommandMessage<Object> commandMessage = GenericCommandMessage.asCommandMessage("test");
        assertTrue(resolver.matches(commandMessage));
        assertEquals(commandMessage.getIdentifier(), resolver.resolveParameterValue(commandMessage));
    }

    @Test
    void testIgnoredWhenNotAnnotated() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(nonAnnotatedMethod, nonAnnotatedMethod.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    void testIgnoredWhenWrongType() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(integerMethod, integerMethod.getParameters(), 0);
        assertNull(resolver);
    }
}
