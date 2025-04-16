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

import com.thoughtworks.xstream.XStream;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.annotation.QueryHandler;
import org.axonframework.springboot.utils.TestSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.core.annotation.Order;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Test class validating the behavior of the {@link InfraConfiguration}.
 *
 * @author Christian Thiel
 */
class InterceptorConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    @Disabled("TODO #3103")
    // TODO #3103 - Revisit this section to adjust it to configurer logic instead of configuration logic.
    public void commandHandlerInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("commandGateway", CommandGateway.class).sendAndWait(new Object());
            //noinspection unchecked
            Queue<String> commandHandlingInterceptingOutcome =
                    context.getBean("commandHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryHandlingInterceptingOutcome =
                    context.getBean("queryHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventHandlingInterceptingOutcome =
                    context.getBean("eventHandlingInterceptingOutcome", Queue.class);

            assertThat(queryHandlingInterceptingOutcome).isEmpty();
            assertThat(eventHandlingInterceptingOutcome).isEmpty();
            assertThat(commandHandlingInterceptingOutcome).hasSize(3);
            assertThat(commandHandlingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(commandHandlingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(commandHandlingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    public void queryHandlerInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("queryGateway", QueryGateway.class).query("foo", String.class);
            //noinspection unchecked
            Queue<String> commandHandlingInterceptingOutcome =
                    context.getBean("commandHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryHandlingInterceptingOutcome =
                    context.getBean("queryHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventHandlingInterceptingOutcome =
                    context.getBean("eventHandlingInterceptingOutcome", Queue.class);

            assertThat(commandHandlingInterceptingOutcome).isEmpty();
            assertThat(eventHandlingInterceptingOutcome).isEmpty();
            assertThat(queryHandlingInterceptingOutcome).hasSize(3);
            assertThat(queryHandlingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(queryHandlingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(queryHandlingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    public void eventHandlerInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("eventGateway", EventGateway.class).publish("foo");

            // Wait for all the event handlers to had their chance.
            CountDownLatch eventHandlerInterceptorInvocations = context.getBean("eventHandlerInterceptorInvocations",
                                                                                CountDownLatch.class);
            assertThat(eventHandlerInterceptorInvocations.await(1, TimeUnit.SECONDS)).isTrue();

            //noinspection unchecked
            Queue<String> commandHandlingInterceptingOutcome =
                    context.getBean("commandHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryHandlingInterceptingOutcome =
                    context.getBean("queryHandlingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventHandlingInterceptingOutcome =
                    context.getBean("eventHandlingInterceptingOutcome", Queue.class);

            assertThat(commandHandlingInterceptingOutcome).isEmpty();
            assertThat(queryHandlingInterceptingOutcome).isEmpty();
            assertThat(eventHandlingInterceptingOutcome).hasSize(3);
            assertThat(eventHandlingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(eventHandlingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(eventHandlingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    @Disabled("TODO #3103")
    // TODO #3103 - Revisit this section to adjust it to configurer logic instead of configuration logic.
    public void commandDispatchInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("commandGateway", CommandGateway.class).sendAndWait(new Object());
            //noinspection unchecked
            Queue<String> commandDispatchingInterceptingOutcome =
                    context.getBean("commandDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryDispatchingInterceptingOutcome =
                    context.getBean("queryDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventDispatchingInterceptingOutcome =
                    context.getBean("eventDispatchingInterceptingOutcome", Queue.class);

            assertThat(queryDispatchingInterceptingOutcome).isEmpty();
            assertThat(eventDispatchingInterceptingOutcome).isEmpty();
            assertThat(commandDispatchingInterceptingOutcome).hasSize(3);
            assertThat(commandDispatchingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(commandDispatchingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(commandDispatchingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    @Disabled("TODO #3103")
    // TODO #3103 - Revisit this section to adjust it to configurer logic instead of configuration logic.
    public void eventDispatchInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("eventGateway", EventGateway.class).publish("foo");
            //noinspection unchecked
            Queue<String> commandDispatchingInterceptingOutcome =
                    context.getBean("commandDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryDispatchingInterceptingOutcome =
                    context.getBean("queryDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventDispatchingInterceptingOutcome =
                    context.getBean("eventDispatchingInterceptingOutcome", Queue.class);

            assertThat(queryDispatchingInterceptingOutcome).isEmpty();
            assertThat(commandDispatchingInterceptingOutcome).isEmpty();
            assertThat(eventDispatchingInterceptingOutcome).hasSize(3);
            assertThat(eventDispatchingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(eventDispatchingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(eventDispatchingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    @Disabled("TODO #3103")
    // TODO #3103 - Revisit this section to adjust it to configurer logic instead of configuration logic.
    public void queryDispatchInterceptorsAreRegisteredInCorrectOrder() {
        testApplicationContext.withUserConfiguration(MessageInterceptorContext.class).run(context -> {
            context.getBean("queryGateway", QueryGateway.class).query("foo", String.class);
            //noinspection unchecked
            Queue<String> commandDispatchingInterceptingOutcome =
                    context.getBean("commandDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> queryDispatchingInterceptingOutcome =
                    context.getBean("queryDispatchingInterceptingOutcome", Queue.class);
            //noinspection unchecked
            Queue<String> eventDispatchingInterceptingOutcome =
                    context.getBean("eventDispatchingInterceptingOutcome", Queue.class);

            assertThat(commandDispatchingInterceptingOutcome).isEmpty();
            assertThat(eventDispatchingInterceptingOutcome).isEmpty();
            assertThat(queryDispatchingInterceptingOutcome).hasSize(3);
            assertThat(queryDispatchingInterceptingOutcome.poll()).startsWith("Order-0");
            assertThat(queryDispatchingInterceptingOutcome.poll()).startsWith("Order-100");
            assertThat(queryDispatchingInterceptingOutcome.poll()).startsWith("Unordered");
        });
    }

    @Test
    public void startsWithSingleInterceptor() {
        testApplicationContext.withUserConfiguration(SlimMessageInterceptorContext.class).run(context -> {
            // We have to access the interceptor to trigger lazy initialization
            context.getBean(SlimMessageInterceptorContext.MyCommandHandlerInterceptor.class);
        });
    }


    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    static class DefaultContext {

        @Bean
        public XStream xStream() {
            return TestSerializer.xStreamSerializer().getXStream();
        }
    }


    static class MessageInterceptorContext {

        @Bean
        public CountDownLatch commandHandlerInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> commandHandlingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        public CountDownLatch queryHandlerInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> queryHandlingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        public CountDownLatch eventHandlerInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> eventHandlingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        public CountDownLatch commandDispatchInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> commandDispatchingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        public CountDownLatch queryDispatchInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> queryDispatchingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        public CountDownLatch eventDispatchInterceptorInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> eventDispatchingInterceptingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        @Order(100)
        public MyGenericHandlerInterceptor myFirstGenericHandlerInterceptor(
                @Qualifier("commandHandlerInterceptorInvocations") CountDownLatch commandHandlerInterceptorInvocations,
                @Qualifier("queryHandlerInterceptorInvocations") CountDownLatch queryHandlerInterceptorInvocations,
                @Qualifier("eventHandlerInterceptorInvocations") CountDownLatch eventHandlerInterceptorInvocations,
                @Qualifier("commandHandlingInterceptingOutcome") Queue<String> commandHandlingInterceptingOutcome,
                @Qualifier("queryHandlingInterceptingOutcome") Queue<String> queryHandlingInterceptingOutcome,
                @Qualifier("eventHandlingInterceptingOutcome") Queue<String> eventHandlingInterceptingOutcome
        ) {
            return new MyGenericHandlerInterceptor("Order-100",
                                                   commandHandlerInterceptorInvocations,
                                                   queryHandlerInterceptorInvocations,
                                                   eventHandlerInterceptorInvocations,
                                                   commandHandlingInterceptingOutcome,
                                                   queryHandlingInterceptingOutcome,
                                                   eventHandlingInterceptingOutcome);
        }

        @Bean
        @Order(0)
        public MyCommandHandlerInterceptor mySecondCommandHandlerInterceptor(
                @Qualifier("commandHandlerInterceptorInvocations") CountDownLatch commandHandlerInterceptorInvocations,
                @Qualifier("commandHandlingInterceptingOutcome") Queue<String> commandHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyCommandHandlerInterceptor("Order-0",
                                                   commandHandlerInterceptorInvocations,
                                                   commandHandlingInterceptingOutcome,
                                                   configuration);
        }

        @Bean
        public MyCommandHandlerInterceptor myThirdCommandHandlerInterceptor(
                @Qualifier("commandHandlerInterceptorInvocations") CountDownLatch commandHandlerInterceptorInvocations,
                @Qualifier("commandHandlingInterceptingOutcome") Queue<String> commandHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyCommandHandlerInterceptor("Unordered",
                                                   commandHandlerInterceptorInvocations,
                                                   commandHandlingInterceptingOutcome,
                                                   configuration);
        }

        @Bean
        @Order(0)
        public MyQueryHandlerInterceptor mySecondQueryHandlerInterceptor(
                @Qualifier("queryHandlerInterceptorInvocations") CountDownLatch queryHandlerInterceptorInvocations,
                @Qualifier("queryHandlingInterceptingOutcome") Queue<String> queryHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyQueryHandlerInterceptor("Order-0",
                                                 queryHandlerInterceptorInvocations,
                                                 queryHandlingInterceptingOutcome,
                                                 configuration);
        }

        @Bean
        public MyQueryHandlerInterceptor myThirdQueryHandlerInterceptor(
                @Qualifier("queryHandlerInterceptorInvocations") CountDownLatch queryHandlerInterceptorInvocations,
                @Qualifier("queryHandlingInterceptingOutcome") Queue<String> queryHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyQueryHandlerInterceptor("Unordered",
                                                 queryHandlerInterceptorInvocations,
                                                 queryHandlingInterceptingOutcome,
                                                 configuration);
        }

        @Bean
        @Order(0)
        public MyEventHandlerInterceptor mySecondEventHandlerInterceptor(
                @Qualifier("eventHandlerInterceptorInvocations") CountDownLatch eventHandlerInterceptorInvocations,
                @Qualifier("eventHandlingInterceptingOutcome") Queue<String> eventHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyEventHandlerInterceptor("Order-0",
                                                 eventHandlerInterceptorInvocations,
                                                 eventHandlingInterceptingOutcome,
                                                 configuration);
        }

        @Bean
        public MyEventHandlerInterceptor myThirdEventHandlerInterceptor(
                @Qualifier("eventHandlerInterceptorInvocations") CountDownLatch eventHandlerInterceptorInvocations,
                @Qualifier("eventHandlingInterceptingOutcome") Queue<String> eventHandlingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyEventHandlerInterceptor("Unordered",
                                                 eventHandlerInterceptorInvocations,
                                                 eventHandlingInterceptingOutcome,
                                                 configuration);
        }


        @Bean
        @Order(100)
        public MyGenericDispatchInterceptor<? extends Message<?>> myFirstGenericDispatcherInterceptor(
                @Qualifier("commandDispatchInterceptorInvocations") CountDownLatch commandDispatchInterceptorInvocations,
                @Qualifier("queryDispatchInterceptorInvocations") CountDownLatch queryDispatchInterceptorInvocations,
                @Qualifier("eventDispatchInterceptorInvocations") CountDownLatch eventDispatchInterceptorInvocations,
                @Qualifier("commandDispatchingInterceptingOutcome") Queue<String> commandDispatchingInterceptingOutcome,
                @Qualifier("queryDispatchingInterceptingOutcome") Queue<String> queryDispatchingInterceptingOutcome,
                @Qualifier("eventDispatchingInterceptingOutcome") Queue<String> eventDispatchingInterceptingOutcome
        ) {
            return new MyGenericDispatchInterceptor<>("Order-100",
                                                      commandDispatchInterceptorInvocations,
                                                      queryDispatchInterceptorInvocations,
                                                      eventDispatchInterceptorInvocations,
                                                      commandDispatchingInterceptingOutcome,
                                                      queryDispatchingInterceptingOutcome,
                                                      eventDispatchingInterceptingOutcome);
        }

        @Bean
        @Order(0)
        public MyCommandDispatchInterceptor mySecondCommandDispatchInterceptor(
                @Qualifier("commandDispatchInterceptorInvocations") CountDownLatch commandDispatchInterceptorInvocations,
                @Qualifier("commandDispatchingInterceptingOutcome") Queue<String> commandDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyCommandDispatchInterceptor("Order-0",
                                                    commandDispatchInterceptorInvocations,
                                                    commandDispatchingInterceptingOutcome,
                                                    configuration);
        }

        @Bean
        public MyCommandDispatchInterceptor myThirdCommandDispatchInterceptor(
                @Qualifier("commandDispatchInterceptorInvocations") CountDownLatch commandDispatchInterceptorInvocations,
                @Qualifier("commandDispatchingInterceptingOutcome") Queue<String> commandDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyCommandDispatchInterceptor("Unordered",
                                                    commandDispatchInterceptorInvocations,
                                                    commandDispatchingInterceptingOutcome,
                                                    configuration);
        }

        @Bean
        @Order(0)
        public MyQueryDispatchInterceptor mySecondQueryDispatchInterceptor(
                @Qualifier("queryDispatchInterceptorInvocations") CountDownLatch queryDispatchInterceptorInvocations,
                @Qualifier("queryDispatchingInterceptingOutcome") Queue<String> queryDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyQueryDispatchInterceptor("Order-0",
                                                  queryDispatchInterceptorInvocations,
                                                  queryDispatchingInterceptingOutcome,
                                                  configuration);
        }

        @Bean
        public MyQueryDispatchInterceptor myThirdQueryDispatchInterceptor(
                @Qualifier("queryDispatchInterceptorInvocations") CountDownLatch queryDispatchInterceptorInvocations,
                @Qualifier("queryDispatchingInterceptingOutcome") Queue<String> queryDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyQueryDispatchInterceptor("Unordered",
                                                  queryDispatchInterceptorInvocations,
                                                  queryDispatchingInterceptingOutcome,
                                                  configuration);
        }

        @Bean
        @Order(0)
        public MyEventDispatchInterceptor mySecondEventDispatchInterceptor(
                @Qualifier("eventDispatchInterceptorInvocations") CountDownLatch eventDispatchInterceptorInvocations,
                @Qualifier("eventDispatchingInterceptingOutcome") Queue<String> eventDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyEventDispatchInterceptor("Order-0",
                                                  eventDispatchInterceptorInvocations,
                                                  eventDispatchingInterceptingOutcome,
                                                  configuration);
        }

        @Bean
        public MyEventDispatchInterceptor myThirdEventDispatchInterceptor(
                @Qualifier("eventDispatchInterceptorInvocations") CountDownLatch eventDispatchInterceptorInvocations,
                @Qualifier("eventDispatchingInterceptingOutcome") Queue<String> eventDispatchingInterceptingOutcome,
                LegacyConfiguration configuration
        ) {
            return new MyEventDispatchInterceptor("Unordered",
                                                  eventDispatchInterceptorInvocations,
                                                  eventDispatchingInterceptingOutcome,
                                                  configuration);
        }

        @Bean
        public CommandHandlingComponent commandHandlingComponent() {
            return new CommandHandlingComponent();
        }

        @Bean
        public QueryHandlingComponent queryHandlingComponent() {
            return new QueryHandlingComponent();
        }

        @Bean
        public EventHandlingComponent eventHandlingComponent() {
            return new EventHandlingComponent();
        }

        public static class MyCommandHandlerInterceptor extends MyHandlerInterceptor<CommandMessage<?>> {

            public MyCommandHandlerInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                               LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyQueryHandlerInterceptor extends MyHandlerInterceptor<QueryMessage<?, ?>> {

            public MyQueryHandlerInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                             LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyEventHandlerInterceptor extends MyHandlerInterceptor<EventMessage<?>> {

            public MyEventHandlerInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                             LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyCommandDispatchInterceptor extends MyDispatchInterceptor<CommandMessage<?>> {

            public MyCommandDispatchInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                                LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyQueryDispatchInterceptor extends MyDispatchInterceptor<QueryMessage<?, ?>> {

            public MyQueryDispatchInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                              LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyEventDispatchInterceptor extends MyDispatchInterceptor<EventMessage<?>> {

            public MyEventDispatchInterceptor(String name, CountDownLatch invocation, Queue<String> handlingOutcome,
                                              LegacyConfiguration configuration) {
                super(name, invocation, handlingOutcome, configuration);
            }
        }

        public static class MyHandlerInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

            private final String name;
            private final CountDownLatch invocation;
            private final Queue<String> handlingOutcome;
            private final LegacyConfiguration axonConfiguration;

            public MyHandlerInterceptor(
                    String name,
                    CountDownLatch invocation,
                    Queue<String> handlingOutcome,
                    LegacyConfiguration axonConfiguration
            ) {
                this.name = name;
                this.invocation = invocation;
                this.handlingOutcome = handlingOutcome;
                this.axonConfiguration = axonConfiguration;
            }

            @Override
            public Object handle(LegacyUnitOfWork<? extends T> unitOfWork,
                                 InterceptorChain interceptorChain) throws Exception {
                invocation.countDown();
                axonConfiguration.tags();
                handlingOutcome.add(name + ": " + unitOfWork.getMessage());
                return interceptorChain.proceedSync();
            }
        }

        public static class MyDispatchInterceptor<T extends Message<?>> implements MessageDispatchInterceptor<T> {

            private final String name;
            private final CountDownLatch invocation;
            private final Queue<String> handlingOutcome;
            private final LegacyConfiguration axonConfiguration;

            public MyDispatchInterceptor(
                    String name,
                    CountDownLatch invocation,
                    Queue<String> handlingOutcome,
                    LegacyConfiguration axonConfiguration
            ) {
                this.name = name;
                this.invocation = invocation;
                this.handlingOutcome = handlingOutcome;
                this.axonConfiguration = axonConfiguration;
            }

            @NotNull
            @Override
            public BiFunction<Integer, T, T> handle(@NotNull List<? extends T> messages) {
                axonConfiguration.tags();
                return (index, message) -> {
                    invocation.countDown();
                    handlingOutcome.add(name + ": " + message);
                    return message;
                };
            }
        }

        public static class MyGenericDispatchInterceptor<T extends Message<?>>
                implements MessageDispatchInterceptor<T> {

            private final String name;
            private final CountDownLatch commandInvocation;
            private final CountDownLatch queryInvocation;
            private final CountDownLatch eventInvocation;
            private final Queue<String> commandHandlingOutcome;
            private final Queue<String> queryHandlingOutcome;
            private final Queue<String> eventHandlingOutcome;

            public MyGenericDispatchInterceptor(String name,
                                                CountDownLatch commandInvocation,
                                                CountDownLatch queryInvocation,
                                                CountDownLatch eventInvocation,
                                                Queue<String> commandHandlingOutcome,
                                                Queue<String> queryHandlingOutcome,
                                                Queue<String> eventHandlingOutcome) {
                this.name = name;
                this.commandInvocation = commandInvocation;
                this.queryInvocation = queryInvocation;
                this.eventInvocation = eventInvocation;
                this.commandHandlingOutcome = commandHandlingOutcome;
                this.queryHandlingOutcome = queryHandlingOutcome;
                this.eventHandlingOutcome = eventHandlingOutcome;
            }

            @NotNull
            @Override
            public BiFunction<Integer, T, T> handle(@NotNull List<? extends T> messages) {
                return (index, message) -> {
                    if (message instanceof CommandMessage) {
                        commandInvocation.countDown();
                        commandHandlingOutcome.add(name);
                    } else if (message instanceof QueryMessage) {
                        queryInvocation.countDown();
                        queryHandlingOutcome.add(name);
                    } else {
                        eventInvocation.countDown();
                        eventHandlingOutcome.add(name);
                    }
                    return message;
                };
            }
        }

        public static class MyGenericHandlerInterceptor implements MessageHandlerInterceptor<Message<?>> {

            private final String name;
            private final CountDownLatch commandInvocation;
            private final CountDownLatch queryInvocation;
            private final CountDownLatch eventInvocation;
            private final Queue<String> commandHandlingOutcome;
            private final Queue<String> queryHandlingOutcome;
            private final Queue<String> eventHandlingOutcome;

            public MyGenericHandlerInterceptor(String name,
                                               CountDownLatch commandInvocation,
                                               CountDownLatch queryInvocation,
                                               CountDownLatch eventInvocation,
                                               Queue<String> commandHandlingOutcome,
                                               Queue<String> queryHandlingOutcome,
                                               Queue<String> eventHandlingOutcome) {
                this.name = name;
                this.commandInvocation = commandInvocation;
                this.queryInvocation = queryInvocation;
                this.eventInvocation = eventInvocation;
                this.commandHandlingOutcome = commandHandlingOutcome;
                this.queryHandlingOutcome = queryHandlingOutcome;
                this.eventHandlingOutcome = eventHandlingOutcome;
            }

            @NotNull
            @Override
            public Object handle(LegacyUnitOfWork<?> unitOfWork,
                                 InterceptorChain interceptorChain) throws Exception {
                if (unitOfWork.getMessage() instanceof CommandMessage) {
                    commandInvocation.countDown();
                    commandHandlingOutcome.add(name);
                } else if (unitOfWork.getMessage() instanceof QueryMessage) {
                    queryInvocation.countDown();
                    queryHandlingOutcome.add(name);
                } else {
                    eventInvocation.countDown();
                    eventHandlingOutcome.add(name);
                }
                return interceptorChain.proceedSync();
            }
        }


        @SuppressWarnings("unused")
        static class CommandHandlingComponent {

            @CommandHandler
            public void handle(Object command) {
            }
        }

        @SuppressWarnings("unused")
        static class QueryHandlingComponent {

            @QueryHandler
            public String handle(String query) {
                return "bar";
            }
        }

        @SuppressWarnings("unused")
        @ProcessingGroup("test")
        static class EventHandlingComponent {

            @EventHandler
            public void handle(String event) {
            }
        }
    }

    static class SlimMessageInterceptorContext {

        @Bean
        public MyCommandHandlerInterceptor myCommandHandlerInterceptor() {
            return new MyCommandHandlerInterceptor();
        }

        public static class MyCommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

            @Override
            public Object handle(@NotNull LegacyUnitOfWork<? extends CommandMessage<?>> unitOfWork,
                                 @NotNull InterceptorChain interceptorChain) throws Exception {
                return interceptorChain.proceedSync();
            }
        }
    }
}
