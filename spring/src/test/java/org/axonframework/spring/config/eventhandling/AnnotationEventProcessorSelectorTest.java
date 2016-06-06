/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.spring.config.StubDomainEvent;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.*;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class AnnotationEventProcessorSelectorTest {

    private AnnotationEventProcessorSelector testSubject;
    private SimpleEventProcessor eventProcessor;

    @Before
    public void setUp() throws Exception {
        eventProcessor = new SimpleEventProcessor("eventProcessor");
        testSubject = new AnnotationEventProcessorSelector(MyInheritedAnnotation.class, eventProcessor);
    }

    @Test
    public void testSelectEventProcessorForAnnotatedHandler() {
        EventProcessor actual = testSubject.selectEventProcessor(new AnnotationEventListenerAdapter(new AnnotatedEventHandler()));
        assertSame(eventProcessor, actual);
    }

    @Test
    public void testSelectEventProcessorForAnnotatedHandlerSubClass() {
        EventProcessor actual = testSubject.selectEventProcessor(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(eventProcessor, actual);
    }

    @Test
    public void testReturnNullWhenNoAnnotationFound() {
        EventProcessor actual = testSubject.selectEventProcessor(new AnnotationEventListenerAdapter(new NonAnnotatedEventHandler()));
        assertNull("EventProcessorSelector should not have selected a eventProcessor", actual);
    }

    @Test
    public void testSelectEventProcessorForNonInheritedHandlerSubClassWhenSuperClassInspectionIsEnabled() {
        testSubject = new AnnotationEventProcessorSelector(MyAnnotation.class, eventProcessor, true);
        EventProcessor actual = testSubject.selectEventProcessor(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(eventProcessor, actual);
    }

    @Test
    public void testReturnNullForNonInheritedHandlerSubClassWhenSuperClassInspectionIsDisabled() {
        testSubject = new AnnotationEventProcessorSelector(MyAnnotation.class, eventProcessor);
        EventProcessor actual = testSubject.selectEventProcessor(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertNull("EventProcessorSelector should not have selected a eventProcessor", actual);
    }

    @MyInheritedAnnotation
    @MyAnnotation
    public static class AnnotatedEventHandler {

        @EventHandler
        public void handle(StubDomainEvent event) {
        }
    }

    public static class AnnotatedSubEventHandler extends AnnotatedEventHandler {

    }

    public static class NonAnnotatedEventHandler {

        @EventHandler
        public void handle(StubDomainEvent event) {
        }
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MyInheritedAnnotation {

    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MyAnnotation {

    }


}
