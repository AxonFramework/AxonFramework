/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;

class HandlerComparatorTest {

    private MessageHandlingMember<?> stringHandler;
    private MessageHandlingMember<?> objectHandler;
    private MessageHandlingMember<?> longHandler;
    private MessageHandlingMember<?> numberHandler;
    private MessageHandlingMember<?> priorityHandler;

    private Comparator<MessageHandlingMember<?>> testSubject;

    @BeforeEach
    void setUp() {
        stringHandler = new StubMessageHandlingMember(String.class, 0);
        objectHandler = new StubMessageHandlingMember(Object.class, 0);
        longHandler = new StubMessageHandlingMember(Long.class, 0);
        numberHandler = new StubMessageHandlingMember(Number.class, 0);
        priorityHandler = new StubMessageHandlingMember(Object.class, 1);

        testSubject = HandlerComparator.instance();
    }

    @Test
    void subclassesBeforeSuperclasses() {
        assertTrue(testSubject.compare(stringHandler, objectHandler) < 0, "String should appear before Object");
        assertTrue(testSubject.compare(objectHandler, stringHandler) > 0, "String should appear before Object");

        assertTrue(testSubject.compare(numberHandler, objectHandler) < 0, "Number should appear before Object");
        assertTrue(testSubject.compare(objectHandler, numberHandler) > 0, "Number should appear before Object");

        assertTrue(testSubject.compare(longHandler, numberHandler) < 0, "Long should appear before Number");
        assertTrue(testSubject.compare(numberHandler, longHandler) > 0, "Long should appear before Number");

        assertTrue(testSubject.compare(longHandler, objectHandler) < 0, "Long should appear before Object");
        assertTrue(testSubject.compare(objectHandler, longHandler) > 0, "Long should appear before Object");
    }

    @Test
    void handlersIsEqualWithItself() {
        assertEquals(0, testSubject.compare(stringHandler, stringHandler));
        assertEquals(0, testSubject.compare(objectHandler, objectHandler));
        assertEquals(0, testSubject.compare(longHandler, longHandler));
        assertEquals(0, testSubject.compare(numberHandler, numberHandler));
        assertEquals(0, testSubject.compare(priorityHandler, priorityHandler));

        assertNotEquals(0, testSubject.compare(stringHandler, objectHandler));
        assertNotEquals(0, testSubject.compare(longHandler, stringHandler));
        assertNotEquals(0, testSubject.compare(numberHandler, stringHandler));
        assertNotEquals(0, testSubject.compare(objectHandler, longHandler));
        assertNotEquals(0, testSubject.compare(objectHandler, numberHandler));
        assertNotEquals(0, testSubject.compare(priorityHandler, numberHandler));
    }

    @Test
    void handlersSortedCorrectly() {
        List<MessageHandlingMember<?>> members = new ArrayList<>(Arrays.asList(objectHandler, numberHandler, stringHandler, longHandler));

        members.sort(this.testSubject);
        assertTrue(members.indexOf(longHandler) < members.indexOf(numberHandler));
        assertEquals(3, members.indexOf(objectHandler));
    }

    @Test
    void notInSameHierarchyUsesPriorityBasedEvaluation() {
        assertTrue(testSubject.compare(priorityHandler, stringHandler) < 0, "priorityHandler should appear before String based on priority");
        assertTrue(testSubject.compare(stringHandler, priorityHandler) > 0, "priorityHandler should appear before String based on priority");
    }

    private static class StubMessageHandlingMember implements MessageHandlingMember<Object> {

        private final Class<?> payloadType;
        private final int priority;

        StubMessageHandlingMember(Class<?> payloadType, int priority) {

            this.payloadType = payloadType;
            this.priority = priority;
        }

        @Override
        public Class<?> payloadType() {
            return payloadType;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean canHandle(@Nonnull Message<?> message) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType) {
            throw new UnsupportedOperationException("Not implemented (yet)");
        }

        @Override
        public Object handle(@Nonnull Message<?> message, Object target) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
            return Optional.empty();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return false;
        }

        @Override
        public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
            return Optional.empty();
        }

        @Override
        public <R> Optional<R> attribute(String attributeKey) {
            return Optional.empty();
        }
    }
}
