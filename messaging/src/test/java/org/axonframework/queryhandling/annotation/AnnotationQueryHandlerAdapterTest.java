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
package org.axonframework.queryhandling.annotation;

import org.axonframework.common.Registration;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotationQueryHandlerAdapter}.
 *
 * @author Marc Gathier
 */
class AnnotationQueryHandlerAdapterTest {

    private AnnotationQueryHandlerAdapter<?> testSubject;

    private final QueryBus queryBus = mock(QueryBus.class);

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationQueryHandlerAdapter<>(new MyQueryHandler());
    }

    @Test
    void subscribe() {
        when(queryBus.subscribe(any(), any(), any())).thenReturn(() -> true);
        Registration registration = testSubject.subscribe(queryBus);

        verify(queryBus, times(1)).subscribe(eq(String.class.getName()), eq(String.class), any());
        verify(queryBus, times(1)).subscribe(eq("Hello"), eq(String.class), any());

        assertTrue(registration.cancel());
    }

    @Test
    void subscribeFailsForHandlerWithInvalidParameters() {
        assertThrows(
                UnsupportedHandlerException.class,
                () -> new AnnotationQueryHandlerAdapter<>(new MySecondQueryHandler())
        );
    }

    @Test
    void subscribeFailsForHandlerWithVoidReturnType() {
        assertThrows(
                UnsupportedHandlerException.class,
                () -> new AnnotationQueryHandlerAdapter<>(new MyThirdQueryHandler())
        );
    }

    @Test
    void handleQuery() throws Exception {
        String testResponse = "hello";
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), testResponse, instanceOf(String.class)
        );
        Object result = testSubject.handleSync(testQuery);

        assertEquals(testResponse, result);
    }

    @Test
    void handleQueryWithException() {
        QueryMessage<String, Integer> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "hello", instanceOf(Integer.class)
        );

        assertThrows(MockException.class, () -> testSubject.handleSync(testQuery));
    }

    @Test
    void handleQueryWithEmptyOptional() throws Exception {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "noEcho", "hello", instanceOf(String.class)
        );

        assertNull(testSubject.handleSync(testQuery));
    }

    @Test
    void handleQueryWithProvidedOptional() throws Exception {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello", "hello", instanceOf(String.class)
        );

        assertEquals("hello", testSubject.handleSync(testQuery));
    }

    @Test
    void handleQueryForCollection() throws Exception {
        int testResponse = 5;
        QueryMessage<Integer, List<String>> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), testResponse, multipleInstancesOf(String.class)
        );

        //noinspection unchecked
        Collection<String> result = (Collection<String>) testSubject.handleSync(testQuery);

        assertEquals(testResponse, result.size());
    }

    @Test
    void handleQueryThrowsNoHandlerForQueryException() {
        QueryMessage<Long, List<String>> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), 42L, multipleInstancesOf(String.class)
        );

        assertThrows(NoHandlerForQueryException.class, () -> testSubject.handleSync(testQuery));
    }

    @Test
    void interceptMessages() throws Exception {
        List<QueryMessage<?, ?>> withInterceptor = new ArrayList<>();
        List<QueryMessage<?, ?>> withoutInterceptor = new ArrayList<>();
        testSubject = new AnnotationQueryHandlerAdapter<>(
                new MyInterceptingQueryHandler(withoutInterceptor, withInterceptor, new ArrayList<>())
        );

        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello", "Hi", instanceOf(String.class)
        );

        String result = (String) testSubject.handleSync(testQuery);

        assertEquals("Hi", result);
        assertEquals(Collections.singletonList(testQuery), withInterceptor);
        assertEquals(Collections.singletonList(testQuery), withoutInterceptor);
    }

    @Test
    void canHandleMessage() {
        QueryMessage<String, Integer> testIntegerQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "hello", instanceOf(Integer.class)
        );
        QueryMessage<String, Long> testLongQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "hello", instanceOf(Long.class)
        );

        assertTrue(testSubject.canHandle(testIntegerQuery));
        assertFalse(testSubject.canHandle(testLongQuery));
    }

    @Test
    @Disabled("TODO #3062 - Exception Handler support")
    void exceptionHandlerAnnotatedMethodsAreSupportedForQueryHandlingComponents() throws Exception {
        List<Exception> interceptedExceptions = new ArrayList<>();
        testSubject = new AnnotationQueryHandlerAdapter<>(
                new MyInterceptingQueryHandler(new ArrayList<>(), new ArrayList<>(), interceptedExceptions)
        );

        QueryMessage<ArrayList<Object>, Object> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), new ArrayList<>(), instanceOf(Object.class)
        );

        Object result = testSubject.handleSync(testQuery);

        assertNull(result);
        assertFalse(interceptedExceptions.isEmpty());
        assertEquals(1, interceptedExceptions.size());
        Exception interceptedException = interceptedExceptions.getFirst();
        assertInstanceOf(RuntimeException.class, interceptedException);
        assertEquals("Some exception", interceptedException.getMessage());
    }

    @SuppressWarnings("unused")
    private static class MyQueryHandler {

        @QueryHandler
        public String echo(String echo) {
            return echo;
        }

        @QueryHandler(queryName = "Hello")
        public Optional<String> echo2(String echo) {
            return Optional.ofNullable(echo);
        }

        @QueryHandler
        public Integer echo3(String echo) {
            throw new MockException("Mock");
        }

        @QueryHandler(queryName = "noEcho")
        public Optional<String> echo4(String echo) {
            return Optional.empty();
        }

        @QueryHandler
        public List<? extends String> echo4(Integer count) {
            List<String> value = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                value.add("echo");
            }
            return value;
        }

        @QueryHandler
        public Object handle(ArrayList<Object> query) {
            throw new RuntimeException("Some exception");
        }
    }

    @SuppressWarnings("unused")
    private static class MySecondQueryHandler {

        @QueryHandler
        public String echo(MetaData metaData, String echo) {
            return echo;
        }
    }

    @SuppressWarnings("unused")
    private static class MyThirdQueryHandler {

        @QueryHandler
        public void echo(String echo) {
        }
    }

    @SuppressWarnings("unused")
    private static class MyInterceptingQueryHandler extends MyQueryHandler {

        private final List<QueryMessage<?, ?>> interceptedWithoutInterceptorChain;
        private final List<QueryMessage<?, ?>> interceptedWithInterceptorChain;
        private final List<Exception> interceptedExceptions;

        private MyInterceptingQueryHandler(List<QueryMessage<?, ?>> interceptedWithoutInterceptorChain,
                                           List<QueryMessage<?, ?>> interceptedWithInterceptorChain,
                                           List<Exception> interceptedExceptions) {
            this.interceptedWithoutInterceptorChain = interceptedWithoutInterceptorChain;
            this.interceptedWithInterceptorChain = interceptedWithInterceptorChain;
            this.interceptedExceptions = interceptedExceptions;
        }

        @MessageHandlerInterceptor
        public void interceptAny(QueryMessage<?, ?> query) {
            interceptedWithoutInterceptorChain.add(query);
        }

        @MessageHandlerInterceptor
        public Object interceptAny(QueryMessage<?, ?> query, InterceptorChain chain) throws Exception {
            interceptedWithInterceptorChain.add(query);
            return chain.proceedSync();
        }

        @ExceptionHandler(resultType = RuntimeException.class)
        public void handle(RuntimeException exception) {
            interceptedExceptions.add(exception);
        }
    }
}
