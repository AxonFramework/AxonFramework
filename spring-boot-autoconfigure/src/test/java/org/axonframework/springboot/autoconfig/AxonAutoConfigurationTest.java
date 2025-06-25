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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.InstantiatedComponentDefinition;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.spring.config.SpringAxonApplication;
import org.axonframework.spring.config.SpringComponentRegistry;
import org.axonframework.spring.config.SpringLifecycleRegistry;
import org.axonframework.spring.config.SpringLifecycleShutdownHandler;
import org.axonframework.spring.config.SpringLifecycleStartHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AxonAutoConfiguration}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public class AxonAutoConfigurationTest {

    private static final String MODULE_SPECIFIC_STRING = "Some String That Is Only Present Here!";
    private static final String FACTORY_SPECIFIC_STRING = "Some Factory Made String.";

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void expectedAxonConfigurationBeansAreAutomaticallyConfigured() {
        testContext.run(context -> {
            assertThat(context).hasBean("springComponentRegistry");
            assertThat(context).hasBean("springLifecycleRegistry");
            assertThat(context).hasBean("axonApplication");
            assertThat(context).hasBean("axonApplicationConfiguration");
        });
    }

    @Test
    void expectedAxonConfigurationBeansCanAllBeCustomized() {
        testContext.withUserConfiguration(CustomContext.class).run(context -> {
            assertThat(context).hasBean("customComponentRegistry");
            assertThat(context).hasBean("customLifecycleRegistry");
            assertThat(context).hasBean("customAxonApplication");
            assertThat(context).hasBean("customAxonApplicationConfiguration");

            assertThat(context).doesNotHaveBean("springComponentRegistry");
            assertThat(context).doesNotHaveBean("springLifecycleRegistry");
            assertThat(context).doesNotHaveBean("axonApplication");
            assertThat(context).doesNotHaveBean("axonApplicationConfiguration");
            assertThat(context).doesNotHaveBean("axonConfiguration");
        });
    }

    @Test
    void lifecycleRegistryIntegratesWithDedicatedSpringLifecycleBeans() {
        testContext.run(context -> {
            // We expect beans to be registered for lifecycle handlers.
            Map<String, SpringLifecycleStartHandler> startHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleStartHandler.class
            );
            Map<String, SpringLifecycleShutdownHandler> shutdownHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleShutdownHandler.class
            );

            // The TestContext registers a start handler on phase 10 for the SimpleCommandBus
            assertTrue(startHandlers.values().stream().anyMatch(h -> h.getPhase() == 10));
            // The TestContext registers a shutdown handler on phase 12 for the SimpleCommandBus
            assertTrue(shutdownHandlers.values().stream().anyMatch(h -> h.getPhase() == 12));

            for (SpringLifecycleStartHandler startHandler : startHandlers.values()) {
                assertTrue(startHandler.isRunning());
            }
            for (SpringLifecycleShutdownHandler shutdownHandler : shutdownHandlers.values()) {
                assertTrue(shutdownHandler.isRunning());
            }

            assertThat(context.getBean("startHandlerInvoked", AtomicBoolean.class)).isTrue();
            assertThat(context.getBean("shutdownHandlerInvoked", AtomicBoolean.class)).isFalse();

            // TODO await response if we can even test this
            // context.stop();
            // await().atMost(Duration.ofSeconds(5))
            //        .pollDelay(Duration.ofMillis(25))
            //        .until(shutdownHandlerInvoked::get);
        });
    }

    @Test
    void validateModuleBeanCreationMethodAddsModuleToAxonConfiguration() {
        testContext.run(context -> {
            assertThat(context).hasBean("testModule");

            AxonConfiguration axonConfiguration = context.getBean(AxonConfiguration.class);
            assertThat(axonConfiguration.getModuleConfigurations()).hasSize(1);
            Optional<org.axonframework.configuration.Configuration> testModuleConfig =
                    axonConfiguration.getModuleConfiguration("testModule");
            assertThat(testModuleConfig).isNotEmpty();
            // Validate if the MODULE_SPECIFIC_STRING, that is only registered by the TestModule internally,
            //  is not in the Application Context.
            assertThat(testModuleConfig.get().getComponent(Object.class, MODULE_SPECIFIC_STRING))
                    .isEqualTo(MODULE_SPECIFIC_STRING);
            assertThat(context).doesNotHaveBean(MODULE_SPECIFIC_STRING);

            // We expect a Module-specific bean to be registered for both a start and shutdown handler.
            Map<String, SpringLifecycleStartHandler> startHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleStartHandler.class
            );
            Map<String, SpringLifecycleShutdownHandler> shutdownHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleShutdownHandler.class
            );

            // The testModule in the TestContext registers a start handler on phase 1337.
            assertTrue(startHandlers.values().stream().anyMatch(h -> h.getPhase() == 1337));
            // The testModule in the TestContext registers a shutdown handler on phase 7331.
            assertTrue(shutdownHandlers.values().stream().anyMatch(h -> h.getPhase() == 7331));

            for (SpringLifecycleStartHandler startHandler : startHandlers.values()) {
                assertTrue(startHandler.isRunning());
            }
            for (SpringLifecycleShutdownHandler shutdownHandler : shutdownHandlers.values()) {
                assertTrue(shutdownHandler.isRunning());
            }

            assertThat(context.getBean("moduleSpecificStartHandlerInvoked", AtomicBoolean.class)).isTrue();
            assertThat(context.getBean("moduleSpecificShutdownHandlerInvoked", AtomicBoolean.class)).isFalse();
        });
    }

    @Test
    void validateComponentFactoryBeanUsage() {
        testContext.run(context -> {
            assertThat(context).hasBean("testComponentFactory");

            Map<String, SpringLifecycleShutdownHandler> shutdownHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleShutdownHandler.class
            );

            // The testComponentFactory in the TestContext registers a shutdown handler on phase 9001.
            assertTrue(shutdownHandlers.values().stream().anyMatch(h -> h.getPhase() == 9001));
            for (SpringLifecycleShutdownHandler shutdownHandler : shutdownHandlers.values()) {
                assertTrue(shutdownHandler.isRunning());
            }

            assertThat(context.getBean("moduleSpecificShutdownHandlerInvoked", AtomicBoolean.class)).isFalse();
        });
    }

    @Configuration
    @EnableAutoConfiguration
    public static class TestContext {

        @Bean
        AtomicBoolean startHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        AtomicBoolean shutdownHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        AtomicBoolean moduleSpecificStartHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        AtomicBoolean moduleSpecificShutdownHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        CommandBus commandBus(LifecycleRegistry lifecycleRegistry,
                              AtomicBoolean startHandlerInvoked,
                              AtomicBoolean shutdownHandlerInvoked) {
            SimpleCommandBus simpleCommandBus = new SimpleCommandBus();
            lifecycleRegistry.onStart(10, () -> startHandlerInvoked.set(true));
            lifecycleRegistry.onShutdown(12, () -> shutdownHandlerInvoked.set(true));
            return simpleCommandBus;
        }

        @Bean
        ConfigurationEnhancer configurationEnhancer() {
            return registry -> registry.registerDecorator(
                    CommandBus.class, 0,
                    (ComponentDecorator<CommandBus, CommandBus>) (config, name, delegate) ->
                            new InterceptingCommandBus(delegate, List.of(), List.of())
            );
        }

        @Bean
        Module testModule(AtomicBoolean moduleSpecificStartHandlerInvoked,
                          AtomicBoolean moduleSpecificShutdownHandlerInvoked) {
            //noinspection rawtypes
            return new BaseModule("testModule") {
                @Override
                public org.axonframework.configuration.Configuration build(
                        @NotNull org.axonframework.configuration.Configuration parent,
                        @NotNull LifecycleRegistry lifecycleRegistry
                ) {
                    lifecycleRegistry.onStart(1337, () -> moduleSpecificStartHandlerInvoked.set(true));
                    lifecycleRegistry.onShutdown(7331, () -> moduleSpecificShutdownHandlerInvoked.set(true));
                    //noinspection unchecked
                    componentRegistry((Consumer<ComponentRegistry>) registry -> registry.registerComponent(
                            Object.class, MODULE_SPECIFIC_STRING, c -> MODULE_SPECIFIC_STRING
                    ));
                    return super.build(parent, lifecycleRegistry);
                }
            };
        }

        @Bean
        ComponentFactory<String> testComponentFactory() {
            return new ComponentFactory<>() {
                @Override
                public void describeTo(@NotNull ComponentDescriptor descriptor) {
                    // Not implemented as not important.
                }

                @NotNull
                @Override
                public Class<String> forType() {
                    return String.class;
                }

                @NotNull
                @Override
                public Optional<Component<String>> construct(
                        @NotNull String name,
                        @NotNull org.axonframework.configuration.Configuration config
                ) {
                    return Optional.of(new InstantiatedComponentDefinition<>(
                            new Component.Identifier<>(forType(), name),
                            name + FACTORY_SPECIFIC_STRING
                    ));
                }

                @Override
                public void registerShutdownHandlers(@NotNull LifecycleRegistry registry) {
                    registry.onShutdown(9001, () -> {
                        // We cannot validate the invocation, but we can validate the constructed SmartLifecycle.
                    });
                }
            };
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class CustomContext {

        @Bean
        SpringComponentRegistry customComponentRegistry(ApplicationContext applicationContext) {
            return new SpringComponentRegistry(applicationContext);
        }

        @Bean
        SpringLifecycleRegistry customLifecycleRegistry() {
            return new SpringLifecycleRegistry();
        }

        @Bean
        SpringAxonApplication customAxonApplication(SpringComponentRegistry customComponentRegistry,
                                                    SpringLifecycleRegistry customLifecycleRegistry) {
            return new SpringAxonApplication(customComponentRegistry, customLifecycleRegistry);
        }

        @Bean
        AxonConfiguration customAxonApplicationConfiguration(SpringAxonApplication customAxonApplication) {
            return customAxonApplication.build();
        }
    }
}
