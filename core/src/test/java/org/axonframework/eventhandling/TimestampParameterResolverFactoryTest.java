/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.annotation.ParameterResolver;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.Temporal;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class TimestampParameterResolverFactoryTest {

    private TimestampParameterResolverFactory testSubject;
    private Timestamp annotation;
    private Method instantMethod;
    private Method temporalMethod;
    private Method stringMethod;
    private Method nonAnnotatedInstantMethod;

    @Before
    public void setUp() throws Exception {
        testSubject = new TimestampParameterResolverFactory();
        instantMethod = getClass().getMethod("someInstantMethod", Instant.class);
        nonAnnotatedInstantMethod = getClass().getMethod("someNonAnnotatedInstantMethod", Instant.class);
        temporalMethod = getClass().getMethod("someTemporalMethod", Temporal.class);
        stringMethod = getClass().getMethod("someStringMethod", String.class);
    }

    public void someInstantMethod(@Timestamp Instant timestamp) {
    }

    public void someNonAnnotatedInstantMethod(Instant timestamp) {
    }

    public void someTemporalMethod(@Timestamp Temporal timestamp) {
    }

    public void someStringMethod(@Timestamp String timestamp) {
    }

    @Test
    public void testResolvesToDateTimeWhenAnnotated() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(instantMethod, instantMethod.getParameters(), 0);
        final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(message));
        assertEquals(message.getTimestamp(), resolver.resolveParameterValue(message));
    }

    @Test
    public void testResolvesToReadableInstantWhenAnnotated() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(temporalMethod,
                                                                temporalMethod.getParameters(),
                                                                0);
        final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(message));
        assertEquals(message.getTimestamp(), resolver.resolveParameterValue(message));
    }

    @Test
    public void testIgnoredWhenNotAnnotated() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(nonAnnotatedInstantMethod,
                                                                nonAnnotatedInstantMethod.getParameters(),
                                                                0);
        assertNull(resolver);
    }

    @Test
    public void testIgnoredWhenWrongType() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(stringMethod, stringMethod.getParameters(), 0);
        assertNull(resolver);
    }
}
