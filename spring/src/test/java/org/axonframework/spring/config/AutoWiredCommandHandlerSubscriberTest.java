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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.spring.config.event.CommandHandlersSubscribedEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

/**
 * Test class validating the invocation order of handler registration and publication of the {@link
 * CommandHandlersSubscribedEvent} through the {@link CommandHandlerSubscriber}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AutoWiredCommandHandlerSubscriberTest {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private CommandBus commandBus;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private CommandHandlersSubscribedEventListener handlersSubscribedEventListener;

    @Test
    void testEventIsPublishedAfterCommandBusSubscription() {
        InOrder subscriptionOrder = inOrder(commandBus, handlersSubscribedEventListener);
        subscriptionOrder.verify(commandBus).subscribe(eq("testCommand"), any());
        subscriptionOrder.verify(commandBus).subscribe(eq("otherTestCommand"), any());
        subscriptionOrder.verify(handlersSubscribedEventListener).on(any());
        subscriptionOrder.verifyNoMoreInteractions();
    }

    @Configuration
    @Import({SpringAxonAutoConfigurer.ImportSelector.class, AnnotationDrivenRegistrar.class})
    public static class Context {

        @Bean
        public CommandBus commandBus() {
            return mock(CommandBus.class);
        }

        @Bean
        public TestCommandHandler testCommandHandler() {
            return new TestCommandHandler();
        }

        @Bean
        public OtherTestCommandHandler otherTestCommandHandler() {
            return new OtherTestCommandHandler();
        }

        @Bean
        public CommandHandlersSubscribedEventListener handlersSubscribedEventListener() {
            return spy(new CommandHandlersSubscribedEventListener());
        }

        // Required by the SpringAxonAutoConfigurer to start
        @Bean
        public EventProcessingModule eventProcessingModule() {
            return mock(EventProcessingModule.class);
        }
    }

    private static class TestCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler(commandName = "testCommand")
        public void handle(String command) {
            // Not important
        }
    }

    private static class OtherTestCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler(commandName = "otherTestCommand")
        public void handle(String command) {
            // Not important
        }
    }

    private static class CommandHandlersSubscribedEventListener {

        @SuppressWarnings("unused")
        @EventListener
        public void on(CommandHandlersSubscribedEvent event) {
            // Not important
        }
    }
}
