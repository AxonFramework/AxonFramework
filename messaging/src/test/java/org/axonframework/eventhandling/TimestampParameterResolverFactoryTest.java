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

package org.axonframework.eventhandling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.Temporal;

import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.Before;
import org.junit.Test;

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
	private Method metaAnnotatedMethod;

    @Before
    public void setUp() throws Exception {
        testSubject = new TimestampParameterResolverFactory();
        instantMethod = getClass().getMethod("someInstantMethod", Instant.class);
        metaAnnotatedMethod = getClass().getMethod("someMetaAnnotatedInstantMethod", Instant.class);
        nonAnnotatedInstantMethod = getClass().getMethod("someNonAnnotatedInstantMethod", Instant.class);
        temporalMethod = getClass().getMethod("someTemporalMethod", Temporal.class);
        stringMethod = getClass().getMethod("someStringMethod", String.class);
    }

    public void someInstantMethod(@Timestamp Instant timestamp) {
    }

    public void someMetaAnnotatedInstantMethod(@CustomTimestamp Instant timestamp) {
    }

    public void someNonAnnotatedInstantMethod(Instant timestamp) {
    }

    public void someTemporalMethod(@Timestamp Temporal timestamp) {
    }

    public void someStringMethod(@Timestamp String timestamp) {
    }

    @Test
    public void testResolvesToDateTimeWhenAnnotated() {
        ParameterResolver resolver = testSubject.createInstance(instantMethod, instantMethod.getParameters(), 0);
        final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(message));
        assertEquals(message.getTimestamp(), resolver.resolveParameterValue(message));
    }

    @Test
    public void testResolvesToReadableInstantWhenAnnotated() {
        ParameterResolver resolver = testSubject.createInstance(temporalMethod,
                                                                temporalMethod.getParameters(),
                                                                0);
        final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(message));
        assertEquals(message.getTimestamp(), resolver.resolveParameterValue(message));
    }

    @Test
    public void testIgnoredWhenNotAnnotated() {
        ParameterResolver resolver = testSubject.createInstance(nonAnnotatedInstantMethod,
                                                                nonAnnotatedInstantMethod.getParameters(),
                                                                0);
        assertNull(resolver);
    }

    @Test
    public void testIgnoredWhenWrongType() {
        ParameterResolver resolver = testSubject.createInstance(stringMethod, stringMethod.getParameters(), 0);
        assertNull(resolver);
    }

	@Test
	public void testResolvesToDateTimeWhenAnnotatedWithMetaAnnotation() {
        ParameterResolver<?> resolver = testSubject.createInstance(metaAnnotatedMethod, metaAnnotatedMethod.getParameters(), 0);
		final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
		assertTrue(resolver.matches(message));
		assertEquals(message.getTimestamp(), resolver.resolveParameterValue(message));
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
	@Timestamp
	private static @interface CustomTimestamp {

	}
}
