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

package org.axonframework.eventhandling;

import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationEventHandlerAdapter}.
 *
 * @author Allard Buijze
 */
class AnnotationEventHandlerAdapterTest {

    private SomeHandler annotatedEventListener;
    private AnnotationEventHandlerAdapter testSubject;

    @BeforeEach
    void setUp() {
        annotatedEventListener = new SomeHandler();
        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(getClass()),
                new SimpleResourceParameterResolverFactory(singletonList(new SomeResource()))
        );
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener, parameterResolverFactory);
    }

    @Test
    void testInvokeResetHandler() {
        testSubject.prepareReset();

        assertTrue(annotatedEventListener.invocations.contains("reset"));
    }

    @Test
    void testInvokeResetHandlerWithResetContext() {
        testSubject.prepareReset("resetContext");

        assertTrue(annotatedEventListener.invocations.contains("resetWithContext"));
    }

    @SuppressWarnings("unused")
    private static class SomeHandler {

        private final List<String> invocations = new ArrayList<>();

        @EventHandler
        public void handle(String event) {
            invocations.add(event);
        }

        @ResetHandler
        public void doReset() {
            invocations.add("reset");
        }

        @ResetHandler
        public void doResetWithContext(String resetContext, SomeResource someResource) {
            invocations.add("resetWithContext");
        }
    }

    private static class SomeResource {
        // Test resource to be resolved as message handling parameter
    }
}
