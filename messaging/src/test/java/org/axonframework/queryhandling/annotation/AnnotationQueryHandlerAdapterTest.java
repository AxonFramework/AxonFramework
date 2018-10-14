/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.utils.MockException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AnnotationQueryHandlerAdapterTest {

    private AnnotationQueryHandlerAdapter<?> testSubject;

    @Mock
    private QueryBus queryBus;

    @Before
    public void setUp() {
        testSubject = new AnnotationQueryHandlerAdapter<>(new MyQueryHandler());
        when(queryBus.subscribe(anyObject(), anyObject(), anyObject())).thenReturn(() -> true);
    }

    @Test
    public void subscribe() {
        Registration registration = testSubject.subscribe(queryBus);

        verify(queryBus, times(1)).subscribe(eq(String.class.getName()), eq(String.class), anyObject());
        verify(queryBus, times(1)).subscribe(eq("Hello"), eq(String.class), anyObject());

        assertTrue(registration.cancel());
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void subscribeFailsForHandlerWithInvalidParameters() {
        new AnnotationQueryHandlerAdapter<>(new MySecondQueryHandler());
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void subscribeFailsForHandlerWithVoidReturnType() {
        new AnnotationQueryHandlerAdapter<>(new MyThirdQueryHandler());
    }

    @Test
    public void testRunQuery() throws Exception {
        String testResponse = "hello";
        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.instanceOf(String.class));
        Object result = testSubject.handle(testQueryMessage);

        assertEquals(testResponse, result);
    }

    @Test(expected = MockException.class)
    public void testRunQueryWithException() throws Exception {
        testSubject.handle(new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRunQueryForCollection() throws Exception {
        int testResponse = 5;
        QueryMessage<Integer, List<String>> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.multipleInstancesOf(String.class));

        Collection<String> result = (Collection<String>) testSubject.handle(testQueryMessage);

        assertEquals(testResponse, result.size());
    }

    @SuppressWarnings("unused")
    private class MyQueryHandler {

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
        public List<? extends String> echo4(Integer count) {
            List<String> value = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                value.add("echo");
            }
            return value;
        }
    }

    @SuppressWarnings("unused")
    private class MySecondQueryHandler {

        @QueryHandler
        public String echo(MetaData metaData, String echo) {
            return echo;
        }
    }

    @SuppressWarnings("unused")
    private class MyThirdQueryHandler {

        @QueryHandler
        public void echo(String echo) {
        }
    }
}
