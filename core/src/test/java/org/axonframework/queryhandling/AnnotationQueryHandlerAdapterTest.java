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
package org.axonframework.queryhandling;

import org.axonframework.common.MockException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.PayloadParameterResolver;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationQueryHandlerAdapterTest {
    private AnnotationQueryHandlerAdapter testSubject;

    @Mock
    private QueryBus queryBus;
    private MyQueryHandler mockTarget;
    private MySecondQueryHandler mockTarget2;
    private AnnotationQueryHandlerAdapter testSubject2;
    private AnnotationQueryHandlerAdapter testSubject3;

    @Before
    public void setUp() throws Exception {
        mockTarget = new MyQueryHandler();
        mockTarget2 = new MySecondQueryHandler();
        testSubject = new AnnotationQueryHandlerAdapter(mockTarget);
        testSubject2 = new AnnotationQueryHandlerAdapter(mockTarget2);
        testSubject3 = new AnnotationQueryHandlerAdapter(new MyThirdQueryHandler());
        when(queryBus.subscribe(anyObject(), anyObject(), anyObject())).thenReturn(() -> true);
    }

    @Test
    public void subscribe() throws Exception {

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
    public void subscribeInvalidParameters() throws Exception {
        testSubject2.subscribe(queryBus);
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void subscribeVoidMethod() throws Exception {
        testSubject3.subscribe(queryBus);
    }

    @Test
    public void testRunQuery() throws Exception {
        Method echo = MyQueryHandler.class.getMethod("echo", String.class);
        ParameterResolver[] parameterResolvers = new ParameterResolver[]{new PayloadParameterResolver(String.class)};
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        testSubject.runQuery(echo, parameterResolvers, mockTarget, queryMessage);
    }

    @Test(expected = QueryExecutionException.class)
    public void testRunQueryWithException() throws Exception {
        Method echo = MyQueryHandler.class.getMethod("echo3", String.class);
        ParameterResolver[] parameterResolvers = new ParameterResolver[]{new PayloadParameterResolver(String.class)};
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        testSubject.runQuery(echo, parameterResolvers, mockTarget, queryMessage);
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
