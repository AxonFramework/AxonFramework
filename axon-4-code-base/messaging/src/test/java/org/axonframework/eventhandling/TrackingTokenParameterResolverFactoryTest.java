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

package org.axonframework.eventhandling;

import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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
        GenericEventMessage<String> message = new GenericEventMessage<>("test");
        assertFalse(resolver.matches(message));
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(1L);
        GenericTrackedEventMessage<String> trackedEventMessage = new GenericTrackedEventMessage<>(trackingToken,
                                                                                       message);
        assertTrue(resolver.matches(trackedEventMessage));
        assertSame(trackingToken, resolver.resolveParameterValue(trackedEventMessage));
    }

    @SuppressWarnings("unused")
    private void method1(Object param1, TrackingToken token) {

    }

}
