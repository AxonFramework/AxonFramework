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

package org.axonframework.messaging.deadletter;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DeadLetterParameterResolverFactory} and
 * {@link DeadLetterParameterResolverFactory.DeadLetterParameterResolver}.
 *
 * @author Steven van Beelen
 */
class DeadLetterParameterResolverFactoryTest {

    private DeadLetterParameterResolverFactory testSubject;

    private Method nonDeadLetterParameterMethod;
    private Method deadLetterMethod;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new DeadLetterParameterResolverFactory();

        nonDeadLetterParameterMethod = getClass().getMethod("nonDeadLetterParameterMethod", Object.class);
        deadLetterMethod = getClass().getMethod("someDeadLetterMethod", DeadLetter.class);
    }

    @AfterEach
    void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void nonDeadLetterParameterMethod(Object event) {
        //Used in setUp()
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someDeadLetterMethod(DeadLetter<?> deadLetter) {
        //Used in setUp()
    }

    @Test
    void ignoredForNonDeadLetterParameterMethod() {
        assertNull(testSubject.createInstance(nonDeadLetterParameterMethod,
                                              nonDeadLetterParameterMethod.getParameters(),
                                              0));
    }

    @Test
    void createsResolverForDeadLetterParameterContainingMethod() {
        assertNotNull(testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0));
    }

    @Test
    void resolverMatchesForAnyMessageType() {
        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertTrue(resolver.matches(GenericCommandMessage.asCommandMessage("some-command")));
        assertTrue(resolver.matches(GenericEventMessage.asEventMessage("some-command")));
        assertTrue(resolver.matches(new GenericQueryMessage<>("some-query", ResponseTypes.instanceOf(String.class))));
    }

    @Test
    void resolvesDeadLetterFromUnitOfWorkResources() {
        EventMessage<String> testMessage = GenericEventMessage.asEventMessage("some-event");
        DeadLetter<EventMessage<String>> expected = new GenericDeadLetter<>(
                "sequenceId", testMessage, new RuntimeException("some-cause")
        );
        UnitOfWork<EventMessage<String>> uow = DefaultUnitOfWork.startAndGet(testMessage);
        uow.resources().put(DeadLetterParameterResolverFactory.CURRENT_DEAD_LETTER, expected);

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        DeadLetter<?> result = resolver.resolveParameterValue(testMessage);
        assertEquals(expected, result);
    }

    @Test
    void resolvesNullWhenNoUnitOfWorkIsActive() {
        Message<Object> testMessage = GenericEventMessage.asEventMessage("some-event");

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertNull(resolver.resolveParameterValue(testMessage));
    }

    @Test
    void resolvesNullWhenNoDeadLetterIsPresentInTheUnitOfWorkResources() {
        EventMessage<String> testMessage = GenericEventMessage.asEventMessage("some-event");
        DefaultUnitOfWork.startAndGet(testMessage);

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertNull(resolver.resolveParameterValue(testMessage));
    }
}