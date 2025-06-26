/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AbstractSagaManager} behavior.
 */
class AbstractSagaManagerTest {

    @Test
    void supportedEventTypesExtractsCorrectTypesFromSagaHandlers() throws Exception {
        // Use reflection to access the private static method
        Method extractMethod = AbstractSagaManager.class.getDeclaredMethod("extractSupportedEventTypes", Class.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Set<Class<?>> supportedTypes = (Set<Class<?>>) extractMethod.invoke(null, TestSaga.class);
        
        assertEquals(3, supportedTypes.size());
        assertTrue(supportedTypes.contains(TestStartEvent.class));
        assertTrue(supportedTypes.contains(TestMiddleEvent.class));
        assertTrue(supportedTypes.contains(TestEndEvent.class));
    }

    @Test
    void supportedEventTypesWithExplicitPayloadType() throws Exception {
        // Use reflection to access the private static method
        Method extractMethod = AbstractSagaManager.class.getDeclaredMethod("extractSupportedEventTypes", Class.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Set<Class<?>> supportedTypes = (Set<Class<?>>) extractMethod.invoke(null, ExplicitPayloadTypeSaga.class);
        
        assertEquals(1, supportedTypes.size());
        assertTrue(supportedTypes.contains(String.class)); // Explicit payload type
    }

    @Test
    void supportedEventTypesIgnoresObjectClassPayloadType() throws Exception {
        // Use reflection to access the private static method
        Method extractMethod = AbstractSagaManager.class.getDeclaredMethod("extractSupportedEventTypes", Class.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Set<Class<?>> supportedTypes = (Set<Class<?>>) extractMethod.invoke(null, ObjectPayloadTypeSaga.class);
        
        assertEquals(0, supportedTypes.size()); // Object.class should be ignored
    }

    /**
     * Test saga with various event handlers.
     */
    public static class TestSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handle(TestStartEvent event) {
            // Handler for TestStartEvent
        }

        @SagaEventHandler(associationProperty = "id")  
        public void handle(TestMiddleEvent event) {
            // Handler for TestMiddleEvent
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "id")
        public void handle(TestEndEvent event) {
            // Handler for TestEndEvent
        }
    }

    /**
     * Test saga with explicit payload type.
     */
    public static class ExplicitPayloadTypeSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "id", payloadType = String.class)
        public void handle(Object event) {
            // Handler with explicit payload type
        }
    }

    /**
     * Test saga with Object.class payload type which should be ignored.
     */
    public static class ObjectPayloadTypeSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "id", payloadType = Object.class)
        public void handle(Object event) {
            // Handler with Object.class payload type should be ignored
        }
    }

    /**
     * Test event classes.
     */
    public static class TestStartEvent {
        private String id;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class TestMiddleEvent {
        private String id;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class TestEndEvent {
        private String id;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
} 