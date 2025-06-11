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

package org.axonframework.springboot.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.spring.config.SpringLifecycleShutdownHandler;
import org.axonframework.spring.config.SpringLifecycleStartHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextHierarchy({
        @ContextConfiguration(name = "parent", classes = {Axon5AutoConfigurationTest.AppConfig.class}),
        @ContextConfiguration(name = "child", classes = {Axon5AutoConfigurationTest.AppOverrideConfig.class})}
)
@TestPropertySource(properties = "axon.axonserver.enabled=false")
@EnableAutoConfiguration
public class Axon5AutoConfigurationTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    org.axonframework.configuration.Configuration axonConfiguration;

    @Autowired
    AxonConfiguration rootConfiguration;

    @Test
    void componentFromChildContextOverruleComponentsFromParent() {
        CommandBus fromRegistry = axonConfiguration.getComponent(CommandBus.class);
        CommandBus fromAppContext = applicationContext.getBean(CommandBus.class);
        CommandBus fromParentAppContext = applicationContext.getParent().getBean(CommandBus.class);
        CommandBus fromParentRegistry = rootConfiguration.getComponent(CommandBus.class);

        assertNotSame(axonConfiguration, rootConfiguration);
        assertNotSame(fromRegistry, fromParentRegistry);

        assertSame(fromRegistry, fromAppContext);
        assertInstanceOf(SimpleCommandBus.class, fromRegistry);

        assertSame(fromParentRegistry, fromParentAppContext);
        assertInstanceOf(InterceptingCommandBus.class, fromParentRegistry);

        assertNotSame(fromAppContext, fromParentAppContext);
        assertNotSame(fromRegistry, fromParentRegistry);
    }

    @Test
    void lifecycleRegistered() {
        // we expect beans to be registered for lifecycle handlers
        Map<String, SpringLifecycleStartHandler> startHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                applicationContext,
                SpringLifecycleStartHandler.class);
        Map<String, SpringLifecycleShutdownHandler> shutdownHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                applicationContext,
                SpringLifecycleShutdownHandler.class);

        assertTrue(startHandlers.values().stream().anyMatch(h -> h.getPhase() == 10));
        assertTrue(shutdownHandlers.values().stream().anyMatch(h -> h.getPhase() == 12));

        for (SpringLifecycleStartHandler startHandler : startHandlers.values()) {
            assertTrue(startHandler.isRunning());
        }
        for (SpringLifecycleShutdownHandler shutdownHandler : shutdownHandlers.values()) {
            assertTrue(shutdownHandler.isRunning());
        }
    }

    // TODO - Add test that validates that the startup and shutdown handlers have been invoked.

    @Configuration("child")
    public static class AppOverrideConfig {

        @Bean
        CommandBus commandBus() {
            return new SimpleCommandBus();
        }
    }

    @Configuration("parent")
    public static class AppConfig {

        @Bean
        CommandBus commandBus(LifecycleRegistry lifecycleRegistry) {
            SimpleCommandBus simpleCommandBus = new SimpleCommandBus();
            lifecycleRegistry.onStart(10, () -> {
            });
            lifecycleRegistry.onShutdown(12, () -> {
            });
            return simpleCommandBus;
        }

        @Bean
        SimpleLifecycleBean simpleLifecycleBean() {
            return new SimpleLifecycleBean();
        }

        @Bean
        ConfigurationEnhancer configurationEnhancer() {
            return registry -> registry.registerDecorator(CommandBus.class, 0,
                                                          (ComponentDecorator<CommandBus, CommandBus>) (config, name, delegate) ->
                                                                  new InterceptingCommandBus(
                                                                          delegate,
                                                                          List.of(), List.of()));
        }

        public static class SimpleLifecycleBean implements SmartLifecycle {

            private final AtomicBoolean running = new AtomicBoolean(false);

            @Override
            public void start() {
                running.set(true);
            }

            @Override
            public void stop() {
                running.set(false);
            }

            @Override
            public boolean isRunning() {
                return running.get();
            }

            @Override
            public int getPhase() {
                return 11;
            }
        }
    }
}
