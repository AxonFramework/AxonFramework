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

package org.axonframework.springboot.autoconfig;

import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

@ContextConfiguration
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AxonServerAutoConfigurationTest {

    private static final TargetContextResolver<Message<?>> CUSTOM_TARGET_CONTEXT_RESOLVER =
            m -> "some-custom-context-resolution";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AxonAutoConfiguration.class,
                    EventProcessingAutoConfiguration.class,
                    InfraConfiguration.class,
                    JdbcAutoConfiguration.class,
                    JpaAutoConfiguration.class,
                    JpaEventStoreAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    NoOpTransactionAutoConfiguration.class,
                    ObjectMapperAutoConfiguration.class,
                    TransactionAutoConfiguration.class
            ));

    @Autowired
    private CommandBus commandBus;
    @Autowired
    @Qualifier("localSegment")
    private CommandBus localSegment;

    @Autowired
    private QueryBus queryBus;
    @Autowired
    private QueryUpdateEmitter updateEmitter;

    @Test
    public void testAxonServerQueryBusConfiguration() {
        assertTrue(queryBus instanceof AxonServerQueryBus);
        assertSame(updateEmitter, queryBus.queryUpdateEmitter());
    }

    @Test
    public void testAxonServerCommandBusBeanTypesConfiguration() {
        assertTrue(commandBus instanceof AxonServerCommandBus);
        assertTrue(localSegment instanceof SimpleCommandBus);
    }

    @Test
    public void testAxonServerDefaultCommandBusConfiguration() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .run(context -> {
                              assertThat(context).getBeanNames(CommandBus.class)
                                                 .hasSize(2);
                              assertThat(context).getBean("axonServerCommandBus")
                                                 .isExactlyInstanceOf(AxonServerCommandBus.class);
                              assertThat(context).getBean("commandBus")
                                                 .isExactlyInstanceOf(SimpleCommandBus.class);
                          });
    }

    @Test
    public void testAxonServerUserDefinedCommandBusConfiguration() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .withUserConfiguration(ExplicitUserCommandBusConfiguration.class)
                          .run(context -> {
                              assertThat(context).getBeanNames(CommandBus.class)
                                                 .hasSize(1);
                              assertThat(context).getBean(CommandBus.class)
                                                 .isExactlyInstanceOf(DisruptorCommandBus.class);
                          });
    }

    @Test
    public void testAxonServerUserDefinedLocalSegmentConfiguration() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .withUserConfiguration(ExplicitUserLocalSegmentConfiguration.class)
                          .run(context -> {
                              assertThat(context).getBeanNames(CommandBus.class)
                                                 .hasSize(2);
                              assertThat(context).getBean("axonServerCommandBus")
                                                 .isExactlyInstanceOf(AxonServerCommandBus.class);
                              assertThat(context).getBean("commandBus")
                                                 .isExactlyInstanceOf(DisruptorCommandBus.class);
                          });
    }

    @Test
    public void testAxonServerWrongUserDefinedLocalSegmentConfiguration() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .withUserConfiguration(ExplicitWrongUserLocalSegmentConfiguration.class)
                          .run(context -> {
                              assertThat(context).getBeanNames(CommandBus.class)
                                                 .hasSize(1);
                              assertThat(context).getBean(CommandBus.class)
                                                 .isExactlyInstanceOf(DisruptorCommandBus.class);
                          });
    }

    @Test
    public void testNonAxonServerCommandBusConfiguration() {
        this.contextRunner.run(context -> {
            assertThat(context).getBeanNames(CommandBus.class)
                               .hasSize(1);
            assertThat(context).getBean(CommandBus.class)
                               .isExactlyInstanceOf(SimpleCommandBus.class);
        });
    }

    @Test
    public void testDefaultTargetContextResolverIsNoOp() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .run(context -> {
                              assertThat(context).getBeanNames(TargetContextResolver.class)
                                                 .hasSize(1);
                              assertThat(context).getBean(TargetContextResolver.class)
                                                 .isEqualTo(TargetContextResolver.noOp());
                          });
    }

    @Test
    public void testCustomTargetContextResolverIsConfigured() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                          .withUserConfiguration(TargetContextResolverConfiguration.class)
                          .run(context -> {
                              assertThat(context).getBeanNames(TargetContextResolver.class)
                                                 .hasSize(1);
                              assertThat(context).getBean(TargetContextResolver.class)
                                                 .isEqualTo(CUSTOM_TARGET_CONTEXT_RESOLVER);
                          });
    }

    private static class ExplicitUserCommandBusConfiguration {

        @Bean
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class ExplicitUserLocalSegmentConfiguration {

        @Bean
        @Qualifier("localSegment")
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class ExplicitWrongUserLocalSegmentConfiguration {

        @Bean
        @Qualifier("wrongSegment")
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class TargetContextResolverConfiguration {

        @Bean
        public TargetContextResolver<Message<?>> customTargetContextResolver() {
            return CUSTOM_TARGET_CONTEXT_RESOLVER;
        }
    }
}
