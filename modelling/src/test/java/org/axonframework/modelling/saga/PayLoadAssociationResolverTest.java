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

package org.axonframework.modelling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.modelling.utils.ConcurrencyUtils.testConcurrent;
import static org.junit.jupiter.api.Assertions.*;

class PayloadAssociationResolverTest {

    private PayloadAssociationResolver testSubject;
    private MessageHandlingMember<Object> handlingMember;
    private static final String TEST_PROPERTY_NAME = "testProperty";
    private static final int TEST_PROPERTY_VALUE = 42;

    @BeforeEach
    void setup() {
        testSubject = new PayloadAssociationResolver();
        handlingMember = new TestHandlingMember();
    }

    @Test
    void setTestPropertyValueIsReturnedFromResolve() {
        testResolveOnce();
    }

    @Test
    void resolveWorksThreadSafe() {
        testConcurrent(4, this::testResolveOnce);
    }

    private void testResolveOnce() {
        EventMessage<?> eventMessage = asEventMessage(new TestEvent(TEST_PROPERTY_VALUE));
        Object result = testSubject.resolve(TEST_PROPERTY_NAME, eventMessage, handlingMember);
        assertEquals(TEST_PROPERTY_VALUE, result);
    }

    private class TestEvent {

        private final int testProperty;

        public TestEvent(int testProperty) {
            this.testProperty = testProperty;
        }

        public int getTestProperty() {
            return testProperty;
        }
    }

    /**
     * Sleep added to {@link #payloadType()} such that the
     * {@link PayLoadAssociationResolverTest#resolveWorksThreadSafe()} test would always fail with a non thread safe
     * map.
     */
    private class TestHandlingMember implements MessageHandlingMember {

        @Override
        public Class<?> payloadType() {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return TestEvent.class;
        }

        @Override
        public Optional<Map<String, Object>> annotationAttributes(Class annotationType) {
            return Optional.empty();
        }

        @Override
        public boolean hasAnnotation(Class annotationType) {
            return false;
        }

        @Override
        public Optional unwrap(Class handlerType) {
            return Optional.empty();
        }

        @Override
        public Object handle(@Nonnull Message message, @Nullable Object target) throws Exception {
            return null;
        }

        @Override
        public boolean canHandleMessageType(@Nonnull Class messageType) {
            return true;
        }

        @Override
        public boolean canHandle(@Nonnull Message message) {
            return true;
        }
    }
}
