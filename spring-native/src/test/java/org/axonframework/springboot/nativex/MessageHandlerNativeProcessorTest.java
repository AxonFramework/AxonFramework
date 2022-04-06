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

package org.axonframework.springboot.nativex;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.jupiter.api.*;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.DefaultNativeReflectionEntry;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeConfigurationRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MessageHandlerNativeProcessor}.
 *
 * @author Steven van Beelen
 */
class MessageHandlerNativeProcessorTest {

    private final MessageHandlerNativeProcessor testSubject = new MessageHandlerNativeProcessor();

    @Test
    void testProcessRegisterBeansContainingMessageHandlers() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        mockBeanFactory(beanFactory);

        NativeConfigurationRegistry registry = new NativeConfigurationRegistry();

        testSubject.process(beanFactory, registry);

        List<Class<?>> registeredTypes = registry.reflection()
                                                 .reflectionEntries()
                                                 .map(DefaultNativeReflectionEntry::getType)
                                                 .collect(Collectors.toList());

        assertTrue(registeredTypes.contains(MyCommandHandlingComponent.class));
        assertTrue(registeredTypes.contains(MyCommand.class));
        assertTrue(registeredTypes.contains(MyEventHandlingComponent.class));
        assertTrue(registeredTypes.contains(MyEvent.class));
        assertTrue(registeredTypes.contains(MyQueryHandlingComponent.class));
        assertTrue(registeredTypes.contains(MyQuery.class));
        assertTrue(registeredTypes.contains(MyQueryResponse.class));
        assertTrue(registeredTypes.contains(MyMessageHandlingComponent.class));
    }

    private void mockBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        when(beanFactory.getBeanDefinitionNames()).thenReturn(new String[]{
                "org.axonframework.springboot.nativex.MyCommandHandlingComponent",
                "org.axonframework.springboot.nativex.MyCommand",
                "org.axonframework.springboot.nativex.MyEventHandlingComponent",
                "org.axonframework.springboot.nativex.MyEvent",
                "org.axonframework.springboot.nativex.MyQueryHandlingComponent",
                "org.axonframework.springboot.nativex.MyQuery",
                "org.axonframework.springboot.nativex.MyQueryResponse",
                "org.axonframework.springboot.nativex.MyMessageHandlingComponent"
        });
        doReturn(MyCommandHandlingComponent.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyCommandHandlingComponent");
        doReturn(MyCommand.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyCommand");
        doReturn(MyEventHandlingComponent.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyEventHandlingComponent");
        doReturn(MyEvent.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyEvent");
        doReturn(MyQueryHandlingComponent.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyQueryHandlingComponent");
        doReturn(MyQuery.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyQuery");
        doReturn(MyQueryResponse.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyQueryResponse");
        doReturn(MyMessageHandlingComponent.class)
                .when(beanFactory)
                .getType("org.axonframework.springboot.nativex.MyMessageHandlingComponent");
    }

    private static class MyCommandHandlingComponent {

        @CommandHandler
        public void handle(MyCommand command) {
            // Do nothing
        }
    }

    private static class MyCommand {

    }

    private static class MyEventHandlingComponent {

        @EventHandler
        public void on(MyEvent event) {
            // Do nothing
        }
    }

    private static class MyEvent {

    }

    private static class MyQueryHandlingComponent {

        @QueryHandler
        public MyQueryResponse handle(MyQuery query) {
            return new MyQueryResponse();
        }
    }

    private static class MyQuery {

    }

    private static class MyQueryResponse {

    }

    private static class MyMessageHandlingComponent {

        @CommandHandler
        public void handle(MyCommand command) {
            // Do nothing
        }

        @EventHandler
        public void on(MyEvent event) {
            // Do nothing
        }

        @QueryHandler
        public MyQueryResponse handle(MyQuery query) {
            return new MyQueryResponse();
        }
    }
}