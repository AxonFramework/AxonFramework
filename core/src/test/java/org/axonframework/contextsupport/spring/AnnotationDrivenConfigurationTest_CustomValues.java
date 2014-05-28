/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AnnotationDrivenConfigurationTest_CustomValues.Context.class)
public class AnnotationDrivenConfigurationTest_CustomValues {

    @Autowired
    private ApplicationContext applicationContext;

    @Qualifier("commandBus")
    @Autowired
    private CommandBus commandBus;

    @Qualifier("eventBus")
    @Autowired
    private EventBus eventBus;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testAnnotationConfigurationAnnotationWrapsBeans() throws Exception {
        Object eventHandler = applicationContext.getBean("eventHandler");
        Object commandHandler = applicationContext.getBean("commandHandler");


        assertTrue(eventHandler instanceof EventListener);
        assertTrue(commandHandler instanceof org.axonframework.commandhandling.CommandHandler);

        verify(commandBus).subscribe(String.class.getName(),
                                     (org.axonframework.commandhandling.CommandHandler<? super Object>) commandHandler);
        verify(eventBus).subscribe((EventListener) eventHandler);
    }

    @Test
    public void testEventListenerPostProcessorBeanDefinitionContainCustomValues() {
        final MutablePropertyValues propertyValues = beanFactory.getBeanDefinition(
                "__axon-annotation-event-listener-bean-post-processor")
                                                                .getPropertyValues();
        assertEquals("-1000", propertyValues.get("phase"));
        assertEquals("true", propertyValues.get("unsubscribeOnShutdown"));
    }

    @Test
    public void testCommandHandlerPostProcessorBeanDefinitionContainCustomValues() {
        final MutablePropertyValues propertyValues = beanFactory.getBeanDefinition(
                "__axon-annotation-command-handler-bean-post-processor")
                                                                .getPropertyValues();
        assertEquals("-1000", propertyValues.get("phase"));
        assertEquals("true", propertyValues.get("unsubscribeOnShutdown"));
    }

    @AnnotationDriven(eventBus = "eventBus", commandBus = "commandBus", unsubscribeOnShutdown = true, phase = -1000)
    @Configuration
    public static class Context {

        @Bean
        public AnnotatedEventHandler eventHandler() {
            return new AnnotatedEventHandler();
        }

        @Bean
        public AnnotatedCommandHandler commandHandler() {
            return new AnnotatedCommandHandler();
        }

        @Bean
        public CommandBus commandBus() {
            return mock(CommandBus.class);
        }

        @Bean
        public CommandBus alternateCommandBus() {
            final CommandBus commandBus = mock(CommandBus.class);
            doThrow(new AssertionError("Should not use this command bus")).when(commandBus).subscribe(anyString(),
                                                                                                      any(AnnotationCommandHandlerAdapter.class));
            return commandBus;
        }

        @Bean
        public EventBus eventBus() {
            return mock(EventBus.class);
        }

        @Bean
        public EventBus alternativeEventBus() {
            final EventBus mock = mock(EventBus.class);
            doThrow(new AssertionError("Should not interact with this event bus"))
                    .when(mock)
                    .subscribe(any(EventListener.class));
            return mock;
        }
    }

    public static class AnnotatedEventHandler {

        @EventHandler
        public void on(String someEvent) {
        }
    }

    public static class AnnotatedCommandHandler {

        @CommandHandler
        public void on(String someEvent) {
        }
    }
}
