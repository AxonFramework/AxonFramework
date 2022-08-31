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
package org.axonframework.queryhandling.annotation;

import org.axonframework.common.Registration;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.instanceOf(String.class));
        Object result = testSubject.handle(testQueryMessage);

        assertEquals(testResponse, result);
    }

    @Test
    void handleQueryWithException() {
        QueryMessage<String, Integer> testQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class));

        assertThrows(MockException.class, () -> testSubject.handle(testQuery));
    }

    @Test
    void handleQueryWithEmptyOptional() throws Exception {
        QueryMessage<String, String> testQuery =
                new GenericQueryMessage<>("hello", "noEcho", ResponseTypes.instanceOf(String.class));

        assertNull(testSubject.handle(testQuery));
    }

    @Test
    void handleQueryWithProvidedOptional() throws Exception {
        QueryMessage<String, String> testQuery =
                new GenericQueryMessage<>("hello", "Hello", ResponseTypes.instanceOf(String.class));

        assertEquals("hello", testSubject.handle(testQuery));
    }

    @Test
    void handleQueryForCollection() throws Exception {
        int testResponse = 5;
        QueryMessage<Integer, List<String>> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.multipleInstancesOf(String.class));

        //noinspection unchecked
        Collection<String> result = (Collection<String>) testSubject.handle(testQueryMessage);

        assertEquals(testResponse, result.size());
    }

    @Test
    void handleQueryThrowsNoHandlerForQueryException() {
        QueryMessage<Long, List<String>> testQueryMessage =
                new GenericQueryMessage<>(42L, ResponseTypes.multipleInstancesOf(String.class));

        assertThrows(NoHandlerForQueryException.class, () -> testSubject.handle(testQueryMessage));
    }

    @Test
    void interceptMessages() throws Exception {
        List<QueryMessage<?, ?>> withInterceptor = new ArrayList<>();
        List<QueryMessage<?, ?>> withoutInterceptor = new ArrayList<>();
        testSubject = new AnnotationQueryHandlerAdapter<>(
                new MyInterceptingQueryHandler(withoutInterceptor, withInterceptor)
        );

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("Hi", "Hello", ResponseTypes.instanceOf(String.class));

        String result = (String) testSubject.handle(testQueryMessage);

        assertEquals("Hi", result);
        assertEquals(Collections.singletonList(testQueryMessage), withInterceptor);
        assertEquals(Collections.singletonList(testQueryMessage), withoutInterceptor);
    }

    @Test
    void canHandleMessage() {
        QueryMessage<String, Integer> testIntegerQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class));
        QueryMessage<String, Long> testLongQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Long.class));

        assertTrue(testSubject.canHandle(testIntegerQuery));
        assertFalse(testSubject.canHandle(testLongQuery));
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

        private MyInterceptingQueryHandler(List<QueryMessage<?, ?>> interceptedWithoutInterceptorChain,
                                           List<QueryMessage<?, ?>> interceptedWithInterceptorChain) {
            this.interceptedWithoutInterceptorChain = interceptedWithoutInterceptorChain;
            this.interceptedWithInterceptorChain = interceptedWithInterceptorChain;
        }

        @MessageHandlerInterceptor
        public void interceptAny(QueryMessage<?, ?> query) {
            interceptedWithoutInterceptorChain.add(query);
        }

        @MessageHandlerInterceptor
        public Object interceptAny(QueryMessage<?, ?> query, InterceptorChain chain) throws Exception {
            interceptedWithInterceptorChain.add(query);
            return chain.proceed();
        }
    }
}
