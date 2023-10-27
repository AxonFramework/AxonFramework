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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class SimpleResourceParameterResolverFactoryTest {

    private static final String TEST_RESOURCE = "testResource";
    private static final Long TEST_RESOURCE2 = 42L;

    private SimpleResourceParameterResolverFactory testSubject;

    private Method messageHandlingMethodWithResourceParameter;
    private Method messageHandlingMethodWithResource2Parameter;
    private Method messageHandlingMethodWithoutResourceParameter;
    private Method messageHandlingMethodWithResourceParameterOfDifferentType;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new SimpleResourceParameterResolverFactory(asList(TEST_RESOURCE, TEST_RESOURCE2));

        messageHandlingMethodWithResourceParameter = getClass().getMethod("someMessageHandlingMethodWithResource", Message.class, String.class);
        messageHandlingMethodWithResource2Parameter = getClass().getMethod("someMessageHandlingMethodWithResource2", Message.class, Long.class);
        messageHandlingMethodWithoutResourceParameter = getClass().getMethod("someMessageHandlingMethodWithoutResource", Message.class);
        messageHandlingMethodWithResourceParameterOfDifferentType =
                getClass().getMethod("someMessageHandlingMethodWithResourceOfDifferentType", Message.class, Integer.class);
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithResource(Message message, String resource) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithResource2(Message message, Long resource) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithoutResource(Message message) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithResourceOfDifferentType(Message message, Integer resourceOfDifferentType) {
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolvesToResourceWhenMessageHandlingMethodHasResourceParameter() {
        ParameterResolver resolver =
                testSubject.createInstance(messageHandlingMethodWithResourceParameter, messageHandlingMethodWithResourceParameter.getParameters(), 1);
        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(eventMessage));
        assertEquals(TEST_RESOURCE, resolver.resolveParameterValue(eventMessage));
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolvesToResourceWhenMessageHandlingMethodHasAnotherResourceParameter() {
        ParameterResolver resolver =
                testSubject.createInstance(messageHandlingMethodWithResource2Parameter, messageHandlingMethodWithResource2Parameter.getParameters(), 1);
        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(eventMessage));
        assertEquals(TEST_RESOURCE2, resolver.resolveParameterValue(eventMessage));
    }

    @Test
    void ignoredWhenMessageHandlingMethodHasNoResourceParameter() {
        ParameterResolver resolver =
                testSubject.createInstance(messageHandlingMethodWithoutResourceParameter, messageHandlingMethodWithoutResourceParameter.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    void ignoredWhenMessageHandlingMethodHasResourceParameterOfDifferentType() {
        ParameterResolver resolver = testSubject.createInstance(messageHandlingMethodWithResourceParameterOfDifferentType, messageHandlingMethodWithResourceParameterOfDifferentType.getParameters(), 1);
        assertNull(resolver);
    }

}
