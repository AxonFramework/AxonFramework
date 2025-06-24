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
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link AxonAutoConfiguration}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public class HierarchicalSpringContextTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        ConfigurableApplicationContext parentContext =
                new SpringApplicationBuilder(ParentContext.class)
                        .web(WebApplicationType.NONE)
                        .build()
                        .run();

        testContext = new ApplicationContextRunner()
                .withUserConfiguration(ChildContext.class)
                .withParent(parentContext)
                .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void expectedBaseAxonBeansAreExpandedWithConfigurationBean() {
        testContext.run(context -> {
            assertThat(context).hasBean("springComponentRegistry");
            assertThat(context).hasBean("springLifecycleRegistry");
            assertThat(context).hasBean("axonApplication");
            assertThat(context).hasBean("axonApplicationConfiguration");
            // The axonConfiguration bean is only created in a hierarchical Spring Application Context.
            assertThat(context).hasBean("axonConfiguration");
        });
    }

    @Test
    void childContextComponentsOverruleParentContextComponents() {
        testContext.run(context -> {
            org.axonframework.configuration.Configuration axonConfiguration =
                    context.getBean(org.axonframework.configuration.Configuration.class);
            AxonConfiguration rootConfiguration = context.getBean(AxonConfiguration.class);
            ApplicationContext parentContext = context.getParent();
            assertThat(parentContext).isNotNull();

            CommandBus busFromRegistry = axonConfiguration.getComponent(CommandBus.class);
            CommandBus busFromAppContext = context.getBean(CommandBus.class);
            CommandBus busFromParentAppContext = parentContext.getBean(CommandBus.class);
            CommandBus busFromParentRegistry = rootConfiguration.getComponent(CommandBus.class);

            assertThat(axonConfiguration).isNotEqualTo(rootConfiguration);
            assertThat(busFromRegistry).isNotEqualTo(busFromParentRegistry);

            assertThat(busFromRegistry).isEqualTo(busFromAppContext);
            assertThat(busFromRegistry).isInstanceOf(SimpleCommandBus.class);

            assertThat(busFromParentRegistry).isEqualTo(busFromParentAppContext);
            assertThat(busFromParentRegistry).isInstanceOf(InterceptingCommandBus.class);

            assertThat(busFromAppContext).isNotEqualTo(busFromParentAppContext);
            assertThat(busFromRegistry).isNotEqualTo(busFromParentRegistry);
        });
    }

    @Configuration("child")
    @EnableAutoConfiguration
    public static class ChildContext {

        @Bean
        CommandBus commandBus() {
            return new SimpleCommandBus();
        }
    }

    @Configuration("parent")
    @EnableAutoConfiguration
    public static class ParentContext {

        @Bean
        CommandBus commandBus() {
            return new SimpleCommandBus();
        }

        @Bean
        ConfigurationEnhancer configurationEnhancer() {
            return registry -> registry.registerDecorator(
                    CommandBus.class, 0,
                    (ComponentDecorator<CommandBus, CommandBus>) (config, name, delegate) ->
                            new InterceptingCommandBus(delegate, List.of(), List.of())
            );
        }
    }
}
