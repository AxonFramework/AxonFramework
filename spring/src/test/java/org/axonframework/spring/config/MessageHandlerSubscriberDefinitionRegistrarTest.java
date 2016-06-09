/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.MessageHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;

import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MessageHandlerSubscriberDefinitionRegistrarTest.Context.class)
public class MessageHandlerSubscriberDefinitionRegistrarTest {

    @Inject
    private EventBus eventBus;
    @Inject
    private EventBus eventBus2;
    @Inject
    private EventProcessor eventProcessor;
    @Inject
    private EventListener eventListener;
    @Inject
    private CommandBus commandBus;
    @Inject
    private MessageHandler<CommandMessage<?>> annotationCommandHandler;

    @Test
    @Ignore
    public void testHandlersRegisteredToEventBus() throws Exception {
//        assertNotNull(eventBus);
//        verify(eventBus).subscribe(eventProcessor);
//        verify(eventBus2, never()).subscribe(eventProcessor);
//        verify(eventProcessor).subscribe(eventListener);
//        verify(commandBus).subscribe(eq(String.class.getName()), eq(annotationCommandHandler));
    }

    @Configuration
    @AnnotationDriven
    @EnableHandlerSubscription(eventBus = "eventBus")
    public static class Context {

        @Bean
        public EventBus eventBus() {
            return mock(EventBus.class);
        }

        @Bean
        public EventBus eventBus2() {
            return mock(EventBus.class);
        }

        @Bean
        public EventProcessor eventProcessor() {
            return mock(EventProcessor.class);
        }

        @Bean
        public CommandBus commandBus() {
            return mock(CommandBus.class);
        }

        @Bean
        public EventListener eventListener() {
            return mock(EventListener.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        public MessageHandler<CommandMessage<?>> simpleCommandHandler() {
            return mock(MessageHandler.class);
        }

        @Bean
        public AnnotatedCommandHandler annotationCommandHandler() {
            return new AnnotatedCommandHandler();
        }

    }

    public static class AnnotatedCommandHandler {

        @CommandHandler
        public void handle(String command) {

        }
    }
}
