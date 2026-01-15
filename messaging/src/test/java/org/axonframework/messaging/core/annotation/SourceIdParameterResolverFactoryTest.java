/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.annotation;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.SourceId;
import org.axonframework.messaging.core.annotation.SourceIdParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SourceIdParameterResolverFactoryTest {

    private SourceIdParameterResolverFactory testSubject;

    private Method sourceIdMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new SourceIdParameterResolverFactory();

        sourceIdMethod = getClass().getMethod("someSourceIdMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings("unused")
    public void someSourceIdMethod(@SourceId String id) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someNonAnnotatedMethod(String id) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someIntegerMethod(@SourceId Integer id) {
        //Used in setUp()
    }

    @Test
    void resolvesToAggregateIdentifierWhenAnnotatedForDomainEventMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(sourceIdMethod, sourceIdMethod.getParameters(), 0);
        EventMessage eventMessage = EventTestUtils.createEvent(0);
        String aggregateId = UUID.randomUUID().toString();
        ProcessingContext context = StubProcessingContext.forMessage(eventMessage, aggregateId, 0L, "aggregateType");
        assertTrue(resolver.matches(context));
        assertEquals(aggregateId, resolver.resolveParameterValue(context).join());
    }

    @Test
    void doesNotMatchWhenAnnotatedForCommandMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(sourceIdMethod, sourceIdMethod.getParameters(), 0);
        CommandMessage commandMessage =
                new GenericCommandMessage(new MessageType("command"), "test");
        ProcessingContext context = StubProcessingContext.forMessage(commandMessage);
        assertFalse(resolver.matches(context));
    }

    @Test
    void ignoredWhenNotAnnotated() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(nonAnnotatedMethod, nonAnnotatedMethod.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    void ignoredWhenWrongType() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(integerMethod, integerMethod.getParameters(), 0);
        assertNull(resolver);
    }
}