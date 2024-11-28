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

package org.axonframework.eventhandling;

import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.junit.jupiter.api.Assertions.*;

class TrackingTokenParameterResolverFactoryTest {

    private Method method;
    private TrackingTokenParameterResolverFactory testSubject;

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getDeclaredMethod("method1", Object.class, TrackingToken.class);
        testSubject = new TrackingTokenParameterResolverFactory();
    }

    @Test
    void createInstance() {
        assertNull(testSubject.createInstance(method, method.getParameters(), 0));
        ParameterResolver<?> resolver = testSubject.createInstance(method, method.getParameters(), 1);

        assertNotNull(resolver);
        EventMessage<String> message = new GenericEventMessage<>(dottedName("test.event"), "test");
        assertFalse(resolver.matches(message, null));
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(1L);
        GenericTrackedEventMessage<String> trackedEventMessage = new GenericTrackedEventMessage<>(trackingToken,
                                                                                                  message);
        assertTrue(resolver.matches(trackedEventMessage, null));
        assertSame(trackingToken, resolver.resolveParameterValue(trackedEventMessage, null));
    }

    @SuppressWarnings("unused")
    private void method1(Object param1, TrackingToken token) {

    }
}
