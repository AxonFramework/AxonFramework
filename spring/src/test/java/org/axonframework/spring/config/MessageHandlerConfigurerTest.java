/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorModule;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


class MessageHandlerConfigurerTest {

    private final ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
    private final ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
    private final ComponentRegistry registry = mock(ComponentRegistry.class);

    @BeforeEach
    void setup() {
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);

        when(beanFactory.getBean(ArgumentMatchers.<String>any())).thenReturn(mock(MyHandler.class));
    }

    @Test
    void detectsAndRegistersCommandHandlersPerPackage() {

        Map<String, String> commandHandlers = new HashMap<>();
        commandHandlers.put("Handler1", "my.command.packaging.Handler1");
        commandHandlers.put("Handler2", "my.command.packaging.Handler2");
        commandHandlers.put("CustomHandler1", "my.command.packaging.custom.Handler1");
        commandHandlers.put("CustomHandler2", "my.command.packaging.custom.Handler2");
        commandHandlers.put("HandlerWithoutPackage", "HandlerWithoutPackage");
        commandHandlers.forEach((String handlerName, String fullQualifiedHandlerName) -> {
                                    var bdmock = beanDefinitionMock(fullQualifiedHandlerName);
                                    when(beanFactory.getBeanDefinition(eq(handlerName))).thenReturn(bdmock);
                                }

        );

        MessageHandlerConfigurer configurer = new MessageHandlerConfigurer(MessageHandlerConfigurer.Type.COMMAND,
                                                                           commandHandlers.keySet().stream().toList());
        configurer.setApplicationContext(applicationContext);
        configurer.enhance(registry);

        var moduleCaptor = ArgumentCaptor.forClass(org.axonframework.configuration.Module.class);
        Mockito.verify(registry, times(3)).registerModule(moduleCaptor.capture());

        var registeredModules = moduleCaptor.getAllValues();
        assertThat(registeredModules).isNotNull();
        assertThat(registeredModules).hasSize(3);
        assertThat(registeredModules).allMatch(module -> module instanceof CommandHandlingModule);
        assertThat(registeredModules.stream().map(Module::name)).containsExactlyInAnyOrder(
                "my.command.packaging",
                "my.command.packaging.custom",
                "default"
        );
    }

    @Test
    void detectsAndRegistersEventHandlersPerPackage() {

        Map<String, String> eventHandlers = new HashMap<>();
        eventHandlers.put("Handler1", "my.event.packaging.Handler1");
        eventHandlers.put("Handler2", "my.event.packaging.Handler2");
        eventHandlers.put("CustomHandler1", "my.event.packaging.custom.Handler1");
        eventHandlers.put("CustomHandler2", "my.event.packaging.custom.Handler2");
        eventHandlers.put("HandlerWithoutPackage", "HandlerWithoutPackage");
        eventHandlers.forEach((String handlerName, String fullQualifiedHandlerName) -> {
                                  var bdmock = beanDefinitionMock(fullQualifiedHandlerName);
                                  when(beanFactory.getBeanDefinition(eq(handlerName))).thenReturn(bdmock);
                              }

        );
        MessageHandlerConfigurer configurer = new MessageHandlerConfigurer(MessageHandlerConfigurer.Type.EVENT,
                                                                           eventHandlers.keySet().stream().toList());
        configurer.setApplicationContext(applicationContext);
        configurer.enhance(registry);

        var moduleCaptor = ArgumentCaptor.forClass(org.axonframework.configuration.Module.class);
        Mockito.verify(registry, times(3)).registerModule(moduleCaptor.capture());

        var registeredModules = moduleCaptor.getAllValues();
        assertThat(registeredModules).isNotNull();
        assertThat(registeredModules).hasSize(3);
        assertThat(registeredModules).allMatch(module -> module instanceof PooledStreamingEventProcessorModule);
        assertThat(registeredModules.stream().map(Module::name)).containsExactlyInAnyOrder(
                "my.event.packaging",
                "my.event.packaging.custom",
                "default"
        );
    }

    @Test
    void detectsAndRegistersQueryHandlersPerPackage() {

        Map<String, String> eventHandlers = new HashMap<>();
        eventHandlers.put("Handler1", "my.query.packaging.Handler1");
        eventHandlers.put("Handler2", "my.query.packaging.Handler2");
        eventHandlers.put("CustomHandler1", "my.query.packaging.custom.Handler1");
        eventHandlers.put("CustomHandler2", "my.query.packaging.custom.Handler2");
        eventHandlers.put("HandlerWithoutPackage", "HandlerWithoutPackage");
        eventHandlers.forEach((String handlerName, String fullQualifiedHandlerName) -> {
                                  var bdmock = beanDefinitionMock(fullQualifiedHandlerName);
                                  when(beanFactory.getBeanDefinition(eq(handlerName))).thenReturn(bdmock);
                              }

        );
        MessageHandlerConfigurer configurer = new MessageHandlerConfigurer(MessageHandlerConfigurer.Type.QUERY,
                                                                           eventHandlers.keySet().stream().toList());
        configurer.setApplicationContext(applicationContext);
        configurer.enhance(registry);

        /*
        TODO -> activate as soon as QueryHandlers are in place
        var moduleCaptor = ArgumentCaptor.forClass(org.axonframework.configuration.Module.class);
        Mockito.verify(registry, times(3)).registerModule(moduleCaptor.capture());

        var registeredModules = moduleCaptor.getAllValues();
        assertThat(registeredModules).isNotNull();
        assertThat(registeredModules).hasSize(3);
        assertThat(registeredModules).allMatch(module -> module instanceof PooledStreamingEventProcessorModule);
        assertThat(registeredModules.stream().map(Module::name)).containsExactlyInAnyOrder(
                "my.query.packaging",
                "my.query.packaging.custom",
                "default"
        );

         */
    }



    private static AbstractBeanDefinition beanDefinitionMock(String fqcn) {
        var bdMock = mock(AbstractBeanDefinition.class);
        when(bdMock.getBeanClassName()).thenReturn(fqcn);
        return bdMock;
    }

    private static class MyHandler {

    }
}