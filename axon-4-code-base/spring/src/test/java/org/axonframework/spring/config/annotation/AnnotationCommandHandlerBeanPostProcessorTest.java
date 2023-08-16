/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageHandler;
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

/**
 * @author Allard Buijze
 */
class AnnotationCommandHandlerBeanPostProcessorTest {

    private AnnotationCommandHandlerBeanPostProcessor testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationCommandHandlerBeanPostProcessor();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void commandHandlerCallsRedirectToAdapter() throws Exception {
        BeanFactory mockBeanFactory = mock(BeanFactory.class);
        testSubject.setBeanFactory(mockBeanFactory);
        Object result1 = testSubject.postProcessBeforeInitialization(new AnnotatedCommandHandler(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof MessageHandler<?>);
        assertTrue(postProcessedBean instanceof AnnotatedCommandHandler);

        MessageHandler<CommandMessage<?>> commandHandler = (MessageHandler<CommandMessage<?>>) postProcessedBean;
        AnnotatedCommandHandler annotatedCommandHandler = (AnnotatedCommandHandler) postProcessedBean;
        CommandMessage<MyCommand> myCommand = GenericCommandMessage.asCommandMessage(new MyCommand());
        commandHandler.handle(myCommand);

        assertEquals(1, annotatedCommandHandler.getInvocationCount());
    }

    @Test
    void commandHandlerCallsRedirectToAdapterWhenUsingCustomAnnotation() throws Exception {
        BeanFactory mockBeanFactory = mock(BeanFactory.class);
        testSubject.setBeanFactory(mockBeanFactory);
        Object result1 = testSubject.postProcessBeforeInitialization(new CustomAnnotatedCommandHandler(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof MessageHandler<?>);
        assertTrue(postProcessedBean instanceof CustomAnnotatedCommandHandler);

        MessageHandler<CommandMessage<?>> commandHandler = (MessageHandler<CommandMessage<?>>) postProcessedBean;
        CustomAnnotatedCommandHandler annotatedCommandHandler = (CustomAnnotatedCommandHandler) postProcessedBean;
        CommandMessage<MyCommand> myCommand = GenericCommandMessage.asCommandMessage(new MyCommand());
        commandHandler.handle(myCommand);

        assertEquals(1, annotatedCommandHandler.getInvocationCount());
    }

    @Test
    void processorIgnoresFactoryBeans() {
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

    public static class AnnotatedCommandHandler {

        private int invocationCount;

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void handleCommand(MyCommand command) {
            invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    public static class CustomAnnotatedCommandHandler {

        private int invocationCount;

        @SuppressWarnings({"UnusedDeclaration"})
        @MyCustomCommand
        public void handleCommand(MyCommand command) {
            invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    private static class MyCommand {

    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @CommandHandler
    private static @interface MyCustomCommand {

    }
}
