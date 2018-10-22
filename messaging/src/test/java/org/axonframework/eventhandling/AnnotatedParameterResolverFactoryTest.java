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

import org.axonframework.messaging.annotation.AbstractAnnotatedParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.Assert.assertEquals;

/**
 * @author Mark Ingram
 */
public class AnnotatedParameterResolverFactoryTest {
    @Test
    public void testTimestampParameterResolverIsReturnedOnlyWhenAppropriate() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("methodWithTimestampParameter", Instant.class, Long.class, Instant.class);
        testMethod(new TimestampParameterResolverFactory(), method,
                   new Class<?>[]{TimestampParameterResolverFactory.TimestampParameterResolver.class, null, null});
    }

    @Test
    public void testSequenceNumberParameterResolverIsReturnedOnlyWhenAppropriate() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("methodWithSequenceNumberParameter", Long.class, Instant.class);
        testMethod(new SequenceNumberParameterResolverFactory(), method,
                   new Class<?>[]{SequenceNumberParameterResolverFactory.SequenceNumberParameterResolver.class, null});
    }

    @Test
    public void testSequenceNumberParameterResolverHandlesPrimitive() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("methodWithPrimitiveParameter", long.class);
        testMethod(new SequenceNumberParameterResolverFactory(), method,
                   new Class<?>[]{SequenceNumberParameterResolverFactory.SequenceNumberParameterResolver.class});
    }

    @SuppressWarnings("unused")
    private static class TestClass {
        public void methodWithTimestampParameter(@Timestamp Instant timestamp, @Timestamp Long wrongType, Instant unannotated) {

        }

        public void methodWithSequenceNumberParameter(@SequenceNumber Long sequenceNumber, @Timestamp Instant different) {

        }

        public void methodWithPrimitiveParameter(@SequenceNumber long primitiveSequenceNumber) {

        }
    }

    private static void testMethod(AbstractAnnotatedParameterResolverFactory<?, ?> factory, Method method, Class<?>[] expectedResolvers) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int param = 0; param < parameterTypes.length; param++) {
            ParameterResolver resolver = factory.createInstance(method, method.getParameters(), param);
            assertEquals("Result incorrect for param: " + param, expectedResolvers[param], resolver != null ? resolver.getClass() : null);
        }
    }
}
