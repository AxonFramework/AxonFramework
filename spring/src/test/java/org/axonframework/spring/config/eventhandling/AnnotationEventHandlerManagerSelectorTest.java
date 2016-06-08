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
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.spring.config.StubDomainEvent;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.*;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class AnnotationEventHandlerManagerSelectorTest {

    private AnnotationEventHandlerManagerSelector testSubject;
    private EventHandlerInvoker eventHandlerInvoker;

    @Before
    public void setUp() throws Exception {
        eventHandlerInvoker = new SimpleEventHandlerInvoker("eventHandlerManager");
        testSubject = new AnnotationEventHandlerManagerSelector(MyInheritedAnnotation.class, eventHandlerInvoker);
    }

    @Test
    public void testSelectEventProcessorForAnnotatedHandler() {
        EventHandlerInvoker actual = testSubject.selectHandlerManager(new AnnotationEventListenerAdapter(new AnnotatedEventHandler()));
        assertSame(eventHandlerInvoker, actual);
    }

    @Test
    public void testSelectEventProcessorForAnnotatedHandlerSubClass() {
        EventHandlerInvoker actual = testSubject.selectHandlerManager(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(eventHandlerInvoker, actual);
    }

    @Test
    public void testReturnNullWhenNoAnnotationFound() {
        EventHandlerInvoker actual = testSubject.selectHandlerManager(new AnnotationEventListenerAdapter(new NonAnnotatedEventHandler()));
        assertNull("Selector should not have selected a eventHandlerManager", actual);
    }

    @Test
    public void testSelectEventProcessorForNonInheritedHandlerSubClassWhenSuperClassInspectionIsEnabled() {
        testSubject = new AnnotationEventHandlerManagerSelector(MyAnnotation.class, eventHandlerInvoker, true);
        EventHandlerInvoker actual = testSubject.selectHandlerManager(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(eventHandlerInvoker, actual);
    }

    @Test
    public void testReturnNullForNonInheritedHandlerSubClassWhenSuperClassInspectionIsDisabled() {
        testSubject = new AnnotationEventHandlerManagerSelector(MyAnnotation.class, eventHandlerInvoker);
        EventHandlerInvoker actual = testSubject.selectHandlerManager(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertNull("Selector should not have selected a eventHandlerManager", actual);
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
