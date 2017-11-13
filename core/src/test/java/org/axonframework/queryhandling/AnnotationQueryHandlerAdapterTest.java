package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Author: marc
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationQueryHandlerAdapterTest {
    private AnnotationQueryHandlerAdapter testSubject;

    @Mock
    private QueryBus queryBus;
    private ParameterResolverFactory parameterResolverFactory;
    private MyQueryHandler mockTarget;
    private MySecondQueryHandler mockTarget2;
    private AnnotationQueryHandlerAdapter testSubject2;
    private AnnotationQueryHandlerAdapter testSubject3;

    @Before
    public void setUp() throws Exception {
        mockTarget = new MyQueryHandler();
        mockTarget2 = new MySecondQueryHandler();
        parameterResolverFactory = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new AnnotationQueryHandlerAdapter(mockTarget, parameterResolverFactory);
        testSubject2 = new AnnotationQueryHandlerAdapter(mockTarget2, parameterResolverFactory);
        testSubject3 = new AnnotationQueryHandlerAdapter(new MyThirdQueryHandler(), parameterResolverFactory);
        when(queryBus.subscribe(anyObject(), anyObject(), anyObject())).thenReturn(() -> true);
    }

    @Test
    public void subscribe() throws Exception {

        Registration registration = testSubject.subscribe(queryBus);
        verify(queryBus, times(1)).subscribe(eq(String.class.getName()),
                eq(String.class.getName()),
                anyObject());

        verify(queryBus, times(1)).subscribe(eq("Hello"),
                eq(String.class.getName()),
                anyObject());
        verify(queryBus, times(1)).subscribe(eq("Hello"),
                eq("HelloResult"),
                anyObject());

        assertTrue(registration.cancel());
    }
    @Test( expected = UnsupportedHandlerException.class)
    public void subscribeInvalidParameters() throws Exception {
        testSubject2.subscribe(queryBus);
    }

    @Test( expected = UnsupportedHandlerException.class)
    public void subscribeVoidMethod() throws Exception {
        testSubject3.subscribe(queryBus);
    }

    @Test
    public void testRunQuery() throws Exception {
        Method echo = MyQueryHandler.class.getMethod("echo", String.class);
        ParameterResolver[] parameterResolvers = new ParameterResolver[1];
        parameterResolvers[0] = parameterResolverFactory.createInstance(echo, echo.getParameters(), 0);
        QueryMessage<String> queryMessage = new GenericQueryMessage<>("hello", String.class.getName());
        testSubject.runQuery(echo, parameterResolvers, mockTarget, queryMessage);
    }

    @Test(expected = QueryExecutionException.class)
    public void testRunQueryWithException() throws Exception {
        Method echo = MyQueryHandler.class.getMethod("echo2", String.class);
        ParameterResolver[] parameterResolvers = new ParameterResolver[1];
        parameterResolvers[0] = parameterResolverFactory.createInstance(echo, echo.getParameters(), 0);
        QueryMessage<String> queryMessage = new GenericQueryMessage<>("hello", String.class.getName());
        testSubject.runQuery(echo, parameterResolvers, mockTarget, queryMessage);
    }

    public class MyQueryHandler {
        @QueryHandler
        public String echo(String echo) {
            return echo;
        }

        @QueryHandler(queryName = "Hello")
        public String echo2(String echo) {
            throw new RuntimeException("This is wrong");
        }

        @QueryHandler(queryName = "Hello", responseName = "HelloResult")
        public String echo3(String echo) {
            return echo;
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