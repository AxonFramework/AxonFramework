/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model;

import org.axonframework.common.annotation.HandlerComparator;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.messaging.Message;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HandlerComparatorTest {

    private MessageHandler stringHandler;
    private MessageHandler objectHandler;
    private MessageHandler longHandler;
    private MessageHandler numberHandler;

    private Comparator<MessageHandler<?>> testSubject;

    @Before
    public void setUp() throws Exception {
        stringHandler = new StubMessageHandler(String.class, 0);
        objectHandler = new StubMessageHandler(Object.class, 0);
        longHandler = new StubMessageHandler(Long.class, 0);
        numberHandler = new StubMessageHandler(Number.class, 1);

        testSubject = HandlerComparator.instance();
    }

    @Test
    public void testSubclassesBeforeSuperclasses() throws Exception {
        assertTrue("String should appear before Object", testSubject.compare(stringHandler, objectHandler) < 0);
        assertTrue("String should appear before Object", testSubject.compare(objectHandler, stringHandler) > 0);

        assertTrue("Number should appear before Object", testSubject.compare(numberHandler, objectHandler) < 0);
        assertTrue("Number should appear before Object", testSubject.compare(objectHandler, numberHandler) > 0);

        assertTrue("Long should appear before Number", testSubject.compare(longHandler, numberHandler) < 0);
        assertTrue("Long should appear before Number", testSubject.compare(numberHandler, longHandler) > 0);

        assertTrue("Long should appear before Object", testSubject.compare(longHandler, objectHandler) < 0);
        assertTrue("Long should appear before Object", testSubject.compare(objectHandler, longHandler) > 0);
    }

    @Test
    public void testHandlersIsEqualWithItself() throws Exception {
        assertEquals(0, testSubject.compare(stringHandler, stringHandler));
        assertEquals(0, testSubject.compare(objectHandler, objectHandler));
        assertEquals(0, testSubject.compare(longHandler, longHandler));
        assertEquals(0, testSubject.compare(numberHandler, numberHandler));
    }

    @Test
    public void testNotInSameHierarchyUsesPriorityBasedEvaluation() throws Exception {
        assertTrue("Number should appear before String based on priority", testSubject.compare(numberHandler, stringHandler) < 0);
        assertTrue("Number should appear before String based on priority", testSubject.compare(stringHandler, numberHandler) > 0);
    }

    private static class StubMessageHandler implements MessageHandler<Object> {
        private final Class<?> payloadType;
        private final int priority;

        public StubMessageHandler(Class<?> payloadType, int priority) {

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
        public boolean canHandle(Message<?> message) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public Object handle(Message<?> message, Object target) throws Exception {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
            return Optional.empty();
        }

        @Override
        public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
            return Optional.empty();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return false;
        }
    }
}
