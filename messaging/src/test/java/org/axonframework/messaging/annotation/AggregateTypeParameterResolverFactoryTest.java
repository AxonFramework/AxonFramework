/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;

class AggregateTypeParameterResolverFactoryTest {

    private AggregateTypeParameterResolverFactory testSubject;

    private Method aggregateTypeMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new AggregateTypeParameterResolverFactory();

        aggregateTypeMethod = getClass().getMethod("someAggregateTypeMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someAggregateTypeMethod(@AggregateType String aggregateType) {
        //Used in setUp()
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someNonAnnotatedMethod(String aggregateType) {
        //Used in setUp()
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someIntegerMethod(@AggregateType Integer messageIdentifier) {
        //Used in setUp()
    }

    @Test
    void resolvesToAggregateTypeWhenAnnotatedForDomainEventMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(aggregateTypeMethod, aggregateTypeMethod.getParameters(), 0);
        final DomainEventMessage<Object> eventMessage =
                new GenericDomainEventMessage<>("aggregateType", "id", 0L, dottedName("test.event"), "payload");
        assertTrue(resolver.matches(eventMessage, null));
        assertEquals(eventMessage.getType(), resolver.resolveParameterValue(eventMessage, null));
    }

    @Test
    void ignoredForNonDomainEventMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(aggregateTypeMethod, aggregateTypeMethod.getParameters(), 0);
        EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertFalse(resolver.matches(eventMessage, null));
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
