/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config.annotation;

import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnnotationQueryHandlerBeanPostProcessorTest {

    private AnnotationQueryHandlerBeanPostProcessor testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationQueryHandlerBeanPostProcessor();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void testQueryHandlerCallsRedirectToAdapter() throws Exception {
        BeanFactory mockBeanFactory = mock(BeanFactory.class);
        testSubject.setBeanFactory(mockBeanFactory);
        Object result1 = testSubject.postProcessBeforeInitialization(new AnnotatedQueryHandler(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof MessageHandler<?>);
        assertTrue(postProcessedBean instanceof AnnotatedQueryHandler);

        MessageHandler<QueryMessage<?, ?>> queryHandler = (MessageHandler<QueryMessage<?, ?>>) postProcessedBean;
        AnnotatedQueryHandler annotatedQueryHandler = (AnnotatedQueryHandler) postProcessedBean;
        QueryMessage<MyQuery, Integer> myCommand = new GenericQueryMessage<>(new MyQuery(), ResponseTypes.instanceOf(Integer.class));

        assertEquals(0, queryHandler.handle(myCommand));
        assertEquals(1, annotatedQueryHandler.getInvocationCount());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void testQueryHandlerCallsRedirectToAdapterWhenUsingCustomAnnotation() throws Exception {
        BeanFactory mockBeanFactory = mock(BeanFactory.class);
        testSubject.setBeanFactory(mockBeanFactory);
        Object result1 = testSubject.postProcessBeforeInitialization(new CustomAnnotatedQueryHandler(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof MessageHandler<?>);
        assertTrue(postProcessedBean instanceof CustomAnnotatedQueryHandler);

        MessageHandler<QueryMessage<?,?>> queryHandler = (MessageHandler<QueryMessage<?, ?>>) postProcessedBean;
        CustomAnnotatedQueryHandler annotatedQueryHandler = (CustomAnnotatedQueryHandler) postProcessedBean;
        QueryMessage<MyQuery, Integer> myCommand = new GenericQueryMessage<>(new MyQuery(), ResponseTypes.instanceOf(Integer.class));

        assertEquals(0, queryHandler.handle(myCommand));
        assertEquals(1, annotatedQueryHandler.getInvocationCount());
    }

    @Test
    void testProcessorIgnoresFactoryBeans() {
        BeanFactory mockBeanFactory = mock(BeanFactory.class);
        when(mockBeanFactory.containsBean("beanName")).thenReturn(true);
        FactoryBean mockFactoryBean = mock(FactoryBean.class);
        testSubject.setBeanFactory(mockBeanFactory);
        Object result1 = testSubject.postProcessBeforeInitialization(mockFactoryBean, "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(mockFactoryBean, "beanName");

        assertSame(mockFactoryBean, result1);
        assertSame(mockFactoryBean, postProcessedBean);
        // this call leads to problems in Spring Boot 2.6 when post-processing factory beans, so it needs to be avoided
        verify(mockBeanFactory, never()).isSingleton(anyString());
    }

    public static class AnnotatedQueryHandler {

        private int invocationCount;

        @SuppressWarnings({"UnusedDeclaration"})
        @QueryHandler
        public Integer handleCommand(MyQuery query) {
            return invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    public static class CustomAnnotatedQueryHandler {

        private int invocationCount;

        @SuppressWarnings({"UnusedDeclaration"})
        @MyCustomQuery
        public Integer handleCommand(MyQuery query) {
            return invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    private static class MyQuery {

    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @QueryHandler
    private static @interface MyCustomQuery {

    }

}
