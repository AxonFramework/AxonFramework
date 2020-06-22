/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.replay;

import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the inner workings of the {@link ResetMessageHandlerDefinition} through the {@link
 * AnnotationEventHandlerAdapter#prepareReset(Object)} method.
 *
 * @author Steven van Beelen
 */
class ResetMessageHandlerDefinitionTest {

    private TestHandler testHandler;
    private AnnotationEventHandlerAdapter handlerAdapter;

    @BeforeEach
    void setUp() {
        testHandler = new TestHandler();
        handlerAdapter = new AnnotationEventHandlerAdapter(testHandler);
    }

    @Test
    void testResetHandlerIsEvaluatedBeforeEventHandler() {
        handlerAdapter.prepareReset(GenericResetMessage.asResetMessage("reset-context"));

        assertTrue(testHandler.resetHandled.get());
        assertFalse(testHandler.eventHandled.get());
    }

    @SuppressWarnings("unused")
    private static class TestHandler {

        private final AtomicBoolean eventHandled = new AtomicBoolean();
        private final AtomicBoolean resetHandled = new AtomicBoolean();

        @EventHandler
        public void on(String event) {
            eventHandled.set(true);
        }

        @ResetHandler
        public void reset(String resetContext) {
            resetHandled.set(true);
        }
    }
}