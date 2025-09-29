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

package org.axonframework.messaging.deadletter;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotations.ParameterResolver;
import org.axonframework.messaging.deadletter.annotations.DeadLetterParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
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
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), "some-command");
        EventMessage testEvent = EventTestUtils.asEventMessage("some-command");
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("query"), "some-query", instanceOf(String.class)
        );

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertTrue(resolver.matches(StubProcessingContext.forMessage(testCommand)));
        assertTrue(resolver.matches(StubProcessingContext.forMessage(testEvent)));
        assertTrue(resolver.matches(StubProcessingContext.forMessage(testQuery)));
    }

    @Test
    void resolvesDeadLetterFromUnitOfWorkResources() {
        EventMessage testMessage = EventTestUtils.asEventMessage("some-event");
        DeadLetter<EventMessage> expected = new GenericDeadLetter<>(
                "sequenceId", testMessage, new RuntimeException("some-cause")
        );
        LegacyUnitOfWork<EventMessage> uow = LegacyDefaultUnitOfWork.startAndGet(testMessage);
        uow.resources().put(DeadLetter.class.getName(), expected);

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        DeadLetter<?> result = resolver.resolveParameterValue(org.axonframework.messaging.unitofwork.StubProcessingContext.forUnitOfWork(
                uow));
        assertEquals(expected, result);
    }

    @Test
    void resolvesNullWhenNoUnitOfWorkIsActive() {
        Message testMessage = EventTestUtils.asEventMessage("some-event");

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertNull(resolver.resolveParameterValue(StubProcessingContext.forMessage(testMessage)));
    }

    @Test
    void resolvesNullWhenNoDeadLetterIsPresentInTheUnitOfWorkResources() {
        EventMessage testMessage = EventTestUtils.asEventMessage("some-event");
        LegacyDefaultUnitOfWork.startAndGet(testMessage);

        ParameterResolver<DeadLetter<?>> resolver =
                testSubject.createInstance(deadLetterMethod, deadLetterMethod.getParameters(), 0);

        assertNull(resolver.resolveParameterValue(StubProcessingContext.forMessage(testMessage)));
    }
}