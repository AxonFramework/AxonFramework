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

import org.axonframework.common.AxonException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class AnnotationEventMessageHandlerAdapterTest {

    @Test
    void testInvokeResetHandler() {
        SomeHandler annotatedEventListener = new SomeHandler();
        new AnnotationEventHandlerAdapter(annotatedEventListener,
                                          MultiParameterResolverFactory.ordered(ClasspathParameterResolverFactory.forClass(getClass()),
                                                                                new SimpleResourceParameterResolverFactory(singletonList(new SomeResource())))).prepareReset();

        assertEquals(singletonList("reset"), annotatedEventListener.invocations);
    }

    @Test
    void testHandlerInterceptors() throws Exception {
        SomeHandler annotatedEventListener = new SomeInterceptingHandler();
        AnnotationEventHandlerAdapter testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                                                      MultiParameterResolverFactory.ordered(ClasspathParameterResolverFactory.forClass(getClass()),
                                                                                                                            new SimpleResourceParameterResolverFactory(singletonList(new SomeResource()))));

        testSubject.handle(asEventMessage("count"));
        assertEquals(3, annotatedEventListener.invocations.stream().filter("count"::equals).count());
    }

    @Test
    void testWrapExceptionInResultInterceptor() {
        Listener annotatedEventListener = new Listener();
        AnnotationEventHandlerAdapter testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                                                      MultiParameterResolverFactory.ordered(ClasspathParameterResolverFactory.forClass(getClass()),
                                                                                                                            new SimpleResourceParameterResolverFactory(singletonList(new SomeResource()))));

        try {
            testSubject.handle(GenericEventMessage.asEventMessage("testing").andMetaData(MetaData.with("key", "value")));
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals("testing", e.getCause().getMessage());
            assertEquals("value", e.getMessage());
        }
    }

    @Test
    void testMismatchingExceptionTypeFromHandlerIgnored() {
        MismatchingExceptionListener annotatedEventListener = new MismatchingExceptionListener();
        AnnotationEventHandlerAdapter testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                                                      MultiParameterResolverFactory.ordered(ClasspathParameterResolverFactory.forClass(getClass()),
                                                                                                                            new SimpleResourceParameterResolverFactory(singletonList(new SomeResource()))));

        try {
            testSubject.handle(GenericEventMessage.asEventMessage("testing").andMetaData(MetaData.with("key", "value")));
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
            assertEquals("testing", e.getMessage());
        }
    }

    private static class Listener {

        @EventHandler
        public void throwException(String msg) {
            throw new IllegalArgumentException(msg);
        }

        @EventHandler
        public void handleNormally(Long value) {
            // noop
        }

        @ExceptionHandler(resultType = IllegalArgumentException.class)
        public void handle(@MetaDataValue(value = "key", required = true) String value, Exception e) {
            throw new RuntimeException(value, e);
        }

    }

    private static class MismatchingExceptionListener {

        @EventHandler
        public void throwException(String msg) {
            throw new IllegalArgumentException(msg);
        }

        @EventHandler
        public void handleNormally(Long value) {
            // noop
        }

        @ExceptionHandler
        public void handle(@MetaDataValue(value = "key", required = true) String value, AxonException e) {
            throw new RuntimeException(value, e);
        }

        @ExceptionHandler(resultType = NoSuchMethodException.class)
        public void handle(Exception e) {
            throw new RuntimeException("This should not have been invoked", e);
        }
    }

    public static class SomeHandler {

        protected List<String> invocations = new ArrayList<>();

        @EventHandler
        public void handle(String event) {
            invocations.add(event);
        }

        @ResetHandler
        public void doReset(SomeResource someResource) {
            invocations.add("reset");
        }
    }

    public static class SomeInterceptingHandler extends SomeHandler {
        @MessageHandlerInterceptor
        public void intercept(String event, InterceptorChain chain) throws Exception {
            invocations.add(event);
            chain.proceed();
        }

        @MessageHandlerInterceptor
        public void intercept(Object any) {
            invocations.add(any.toString());
        }

    }

    public static class SomeResource {
    }
}
