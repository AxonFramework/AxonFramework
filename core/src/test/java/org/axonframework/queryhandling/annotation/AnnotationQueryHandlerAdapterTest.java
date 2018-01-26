/*
 * Copyright (c) 2010-2017. Axon Framework
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
package org.axonframework.queryhandling.annotation;

import org.axonframework.common.MockException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AnnotationQueryHandlerAdapterTest {

    private AnnotationQueryHandlerAdapter<?> testSubject;

    @Mock
    private QueryBus queryBus;
    private MySecondQueryHandler mockTarget2;

    @Before
    public void setUp() throws Exception {
        mockTarget2 = new MySecondQueryHandler();
        testSubject = new AnnotationQueryHandlerAdapter<>(new MyQueryHandler());
        when(queryBus.subscribe(anyObject(), anyObject(), anyObject())).thenReturn(() -> true);
    }

    @Test
    public void subscribe() {

        Registration registration = testSubject.subscribe(queryBus);
        verify(queryBus, times(1)).subscribe(eq(String.class.getName()),
                                             eq(String.class),
                                             anyObject());

        verify(queryBus, times(1)).subscribe(eq("Hello"),
                                             eq(String.class),
                                             anyObject());

        assertTrue(registration.cancel());
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void subscribeInvalidParameters() {
        new AnnotationQueryHandlerAdapter<>(mockTarget2);
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void subscribeVoidMethod() {
        new AnnotationQueryHandlerAdapter<>(new MyThirdQueryHandler());
    }

    @Test
    public void testRunQuery() throws Exception {
        QueryMessage<String, String> queryMessage =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(String.class));
        Object result = testSubject.handle(queryMessage);
        assertEquals("hello", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRunQueryForCollection() throws Exception {
        QueryMessage<Integer, String> queryMessage =
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(String.class));
        Collection<String> result = (Collection<String>) testSubject.handle(queryMessage);
        assertEquals(5, result.size());
    }

    @Test(expected = MockException.class)
    public void testRunQueryWithException() throws Exception {
        QueryMessage<String, Integer> queryMessage =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class));
        testSubject.handle(queryMessage);
    }

    @Test
    public void testExplicitlyDeclaredReturnType() throws Exception {
        QueryMessage<String, BigDecimal> queryMessage =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(BigDecimal.class));
        Object result = testSubject.handle(queryMessage);

        assertEquals(1, ((Collection) result).size());
        assertEquals(BigDecimal.ONE, ((Collection) result).iterator().next());
    }

    public class MyQueryHandler {

        @QueryHandler
        public String echo(String echo) {
            return echo;
        }

        @QueryHandler(queryName = "Hello")
        public String echo2(String echo) {
            return echo;
        }

        @QueryHandler
        public Integer echo3(String echo) {
            throw new MockException("Mock");
        }

        @QueryHandler
        public List<? extends String> echo(Integer count) {
            List<String> value = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                value.add("echo");
            }
            return value;
        }
    }

    public class MySecondQueryHandler {

        @QueryHandler
        public String echo(MetaData metaData, String echo) {
            return echo;
        }
    }

    public class MyThirdQueryHandler {

        @QueryHandler
        public void echo(String echo) {
        }
    }
}
