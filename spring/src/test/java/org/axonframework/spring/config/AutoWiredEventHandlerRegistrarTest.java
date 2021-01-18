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

import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.config.event.EventHandlersSubscribedEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

/**
 * Test class validating the invocation order of handler registration and publication of the {@link
 * EventHandlersSubscribedEvent} through the {@link EventHandlerRegistrar}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AutoWiredEventHandlerRegistrarTest {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private EventProcessingModule eventProcessingConfigurer;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private EventHandlersSubscribedEventListener handlersSubscribedEventListener;

    @Test
    void testEventIsPublishedAfterEventProcessingConfigurerSubscription() {
        InOrder subscriptionOrder = inOrder(eventProcessingConfigurer, handlersSubscribedEventListener);
        subscriptionOrder.verify(eventProcessingConfigurer, times(2)).registerEventHandler(any());
        subscriptionOrder.verify(handlersSubscribedEventListener).onApplicationEvent(any());
        subscriptionOrder.verify(eventProcessingConfigurer).initialize(any());
        subscriptionOrder.verifyNoMoreInteractions();
    }

    @Configuration
    @Import({SpringAxonAutoConfigurer.ImportSelector.class, AnnotationDrivenRegistrar.class})
    public static class Context {

        @Bean
        public EventProcessingModule eventProcessingConfigurer() {
            return mock(EventProcessingModule.class);
        }

        @Bean
        public TestEventHandler testEventHandler() {
            return new TestEventHandler();
        }

        @Bean
        public OtherTestEventHandler otherTestEventHandler() {
            return new OtherTestEventHandler();
        }

        @Bean
        public EventHandlersSubscribedEventListener handlersSubscribedEventListener() {
            return spy(new EventHandlersSubscribedEventListener());
        }
    }

    private static class TestEventHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(String event) {
            // Not important
        }
    }

    private static class OtherTestEventHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(String event) {
            // Not important
        }
    }

    private static class EventHandlersSubscribedEventListener implements
            ApplicationListener<EventHandlersSubscribedEvent> {

        @SuppressWarnings("unused")
        @Override
        public void onApplicationEvent(@SuppressWarnings("NullableProblems") EventHandlersSubscribedEvent event) {
            // Not important
        }
    }
}
