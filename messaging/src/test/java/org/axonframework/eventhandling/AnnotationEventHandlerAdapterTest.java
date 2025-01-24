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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonException;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationEventHandlerAdapter}.
 *
 * @author Allard Buijze
 */
class AnnotationEventHandlerAdapterTest {

    private SomeHandler annotatedEventListener;
    private ParameterResolverFactory parameterResolverFactory;
    private AnnotationEventHandlerAdapter testSubject;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
        annotatedEventListener = new SomeHandler();
        parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(getClass()),
                new SimpleResourceParameterResolverFactory(singletonList(new SomeResource()))
        );
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                        parameterResolverFactory,
                                                        messageTypeResolver);
    }

    @Test
    void invokeResetHandler() {
        testSubject.prepareReset(null);

        assertTrue(annotatedEventListener.invocations.contains("reset"));
    }

    @Test
    void invokeResetHandlerWithResetContext() {
        testSubject.prepareReset("resetContext", null);

        assertTrue(annotatedEventListener.invocations.contains("resetWithContext"));
    }

    @Test
    void handlerInterceptors() throws Exception {
        SomeHandler annotatedEventListener = new SomeInterceptingHandler();
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                        parameterResolverFactory,
                                                        messageTypeResolver);

        testSubject.handleSync(asEventMessage("count"));
        assertEquals(3, annotatedEventListener.invocations.stream().filter("count"::equals).count());
    }

    @Test
    @Disabled("TODO #3062 - Exception Handler support")
    void wrapExceptionInResultInterceptor() {
        EventMessage<Object> testEventMessage =
                asEventMessage("testing").andMetaData(MetaData.with("key", "value"));

        SomeExceptionHandler annotatedEventListener = new SomeExceptionHandler();
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                        parameterResolverFactory,
                                                        messageTypeResolver);

        try {
            testSubject.handleSync(testEventMessage);
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals("testing", e.getCause().getMessage());
            assertEquals("value", e.getMessage());
        }
    }

    @Test
    void mismatchingExceptionTypeFromHandlerIgnored() {
        EventMessage<Object> testEventMessage =
                asEventMessage("testing").andMetaData(MetaData.with("key", "value"));

        SomeMismatchingExceptionHandler annotatedEventListener = new SomeMismatchingExceptionHandler();
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                        parameterResolverFactory,
                                                        messageTypeResolver);

        try {
            testSubject.handleSync(testEventMessage);
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
            assertEquals("testing", e.getMessage());
        }
    }

    @Test
    void canHandleTypeDoesNotReturnResetHandlers() {
        SomeResetHandlerWithContext annotatedEventListener = new SomeResetHandlerWithContext();
        testSubject = new AnnotationEventHandlerAdapter(annotatedEventListener,
                                                        parameterResolverFactory,
                                                        messageTypeResolver);

        assertTrue(testSubject.canHandleType(Long.class));
        assertFalse(testSubject.canHandleType(String.class));
        assertFalse(testSubject.canHandleType(Integer.class));
    }

    @Test
    void replayNotSupportedOnSingleHandler() {
        SingleReplayBlockingHandler handler = new SingleReplayBlockingHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, parameterResolverFactory, messageTypeResolver);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void replayNotSupportedOnClassLevel() {
        ReplayBlockedOnClassLevelHandler handler = new ReplayBlockedOnClassLevelHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, parameterResolverFactory, messageTypeResolver);

        assertFalse(testSubject.supportsReset());
    }

    @Test
    void replayNotSupportedOnClassLevelWithHandlerLevelOverride() {
        ReplayBlockedOnClassLevelWithReplayCapableHandler handler = new ReplayBlockedOnClassLevelWithReplayCapableHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, parameterResolverFactory, messageTypeResolver);

        assertTrue(testSubject.supportsReset());
    }


    @SuppressWarnings("unused")
    private static class SomeHandler {

        final List<String> invocations = new ArrayList<>();

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

    @SuppressWarnings("unused")
    private static class SomeExceptionHandler {

        @EventHandler
        public void throwException(String msg) {
            throw new IllegalArgumentException(msg);
        }

        @EventHandler
        public void handleNormally(Long value) {
            // No-op
        }

        @ExceptionHandler(resultType = IllegalArgumentException.class)
        public void handle(@MetaDataValue(value = "key", required = true) String value, Exception e) {
            throw new RuntimeException(value, e);
        }
    }

    @SuppressWarnings("unused")
    private static class SomeMismatchingExceptionHandler {

        @EventHandler
        public void throwException(String msg) {
            throw new IllegalArgumentException(msg);
        }

        @EventHandler
        public void handleNormally(Long value) {
            // No-op
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

    @SuppressWarnings("unused")
    public static class SomeInterceptingHandler extends SomeHandler {

        @MessageHandlerInterceptor
        public void intercept(String event, InterceptorChain chain) throws Exception {
            invocations.add(event);
            chain.proceedSync();
        }

        @MessageHandlerInterceptor
        public void intercept(Object any) {
            invocations.add(any.toString());
        }
    }

    @SuppressWarnings("unused")
    public static class SomeResetHandlerWithContext {

        @EventHandler
        public void handle(Long event) {
            // No-op
        }

        @ResetHandler
        public void reset() {
            // No-op
        }

        @ResetHandler
        public void resetWithContext(String resetContext, SomeResource someResource) {
            // No-op
        }
    }

    public static class SingleReplayBlockingHandler {

        @EventHandler
        public void handle(Long event) {
            // No-op
        }

        @DisallowReplay
        @EventHandler
        public void handle(String event) {
            // No-op
        }

    }

    @DisallowReplay
    public static class ReplayBlockedOnClassLevelHandler {

        @EventHandler
        public void handle(Long event) {
            // No-op
        }

        @EventHandler
        public void handle(String event) {
            // No-op
        }

    }

    @DisallowReplay
    public static class ReplayBlockedOnClassLevelWithReplayCapableHandler {

        @EventHandler
        public void handle(Long event) {
            // No-op
        }

        @AllowReplay
        @EventHandler
        public void handle(String event) {
            // No-op
        }

    }


    private static class SomeResource {
        // Test resource to be resolved as message handling parameter
    }
}
