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
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.spring.config.MessageHandlerLookup;
import org.axonframework.spring.config.SpringAggregateLookup;
import org.axonframework.spring.config.SpringAxonConfiguration;
import org.axonframework.spring.config.SpringConfigurer;
import org.axonframework.spring.config.SpringSagaLookup;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.core.annotation.Order;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Test class validating the behavior of the {@link InfraConfiguration}.
 *
 * @author Steven van Beelen
 */
class InfraConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void infraConfigurationConstructsSpringConfigurerAndSpringAxonConfiguration() {
        testApplicationContext.run(context -> {
            assertThat(context).hasSingleBean(SpringConfigurer.class);
            assertThat(context).getBean("springAxonConfigurer")
                               .isInstanceOf(SpringConfigurer.class);
            assertThat(context).hasSingleBean(SpringAxonConfiguration.class);
            // The SpringAxonConfiguration is a factory for the DefaultConfigurer.ConfigurationImpl that is private.
            // Hence, we perform a non-null check.
            assertThat(context).getBean("springAxonConfiguration")
                               .isNotNull();
        });
    }

    @Test
    void infraConfigurationConstructsLookUpBeans() {
        testApplicationContext.run(context -> {
            assertThat(context).hasSingleBean(MessageHandlerLookup.class);
            assertThat(context).hasSingleBean(SpringAggregateLookup.class);
            assertThat(context).hasSingleBean(SpringSagaLookup.class);
        });
    }

    @Test
    void multipleEventUpcasterChainBeansAreCombinedInSingleEventUpcasterChainThroughConfiguration() {
        //noinspection unchecked
        Stream<IntermediateEventRepresentation> mockStream = mock(Stream.class);

        testApplicationContext.withUserConfiguration(UpcasterContext.class).run(context -> {
            assertThat(context).getBean("firstUpcasterChain")
                               .isInstanceOf(EventUpcasterChain.class);
            assertThat(context).getBean("secondUpcasterChain")
                               .isInstanceOf(EventUpcasterChain.class);

            EventUpcasterChain result = context.getBean("springAxonConfiguration", Configuration.class)
                                               .upcasterChain();
            assertThat(result).isNotNull();

            result.upcast(mockStream);

            // Verify map invocation first and sorted invocation second, as the EventUpcasterChain beans are ordered in that fashion.
            InOrder upcasterOrder = inOrder(mockStream);
            //noinspection ResultOfMethodCallIgnored
            upcasterOrder.verify(mockStream).map(any());
            //noinspection ResultOfMethodCallIgnored
            upcasterOrder.verify(mockStream).sorted();
        });
    }

    /**
     * This form of integration test is added in the {@link InfraConfiguration} test class, as it is in charge of
     * constructing the {@link MessageHandlerLookup} bean. It's this bean that finds all the message handlers and should
     * provide them in a sorted fashion.
     */
    @Test
    void eventHandlingComponentsAreRegisteredAccordingToOrderAnnotation() {
        testApplicationContext.withUserConfiguration(EventHandlerOrderingContext.class).run(context -> {
            // Validate existence of Event Processor "test"
            assertThat(context).getBean("eventProcessingModule")
                               .isNotNull();
            EventProcessingModule eventProcessingModule =
                    context.getBean("eventProcessingModule", EventProcessingModule.class);
            assertThat(eventProcessingModule.eventProcessor("test")).isPresent();

            assertThat(context).getBean("eventHandlerInvocations", CountDownLatch.class)
                               .isNotNull();
            CountDownLatch eventHandlerInvocations =
                    context.getBean("eventHandlerInvocations", CountDownLatch.class);

            String testEvent = "some-event-payload";
            context.getBean(EventGateway.class)
                   .publish(testEvent);

            // Wait for all the event handlers to had their chance.
            assertThat(eventHandlerInvocations.await(1, TimeUnit.SECONDS)).isTrue();

            assertThat(context).getBean("handlingOutcome", Queue.class)
                               .isNotNull();
            //noinspection unchecked
            Queue<String> handlingOrder = context.getBean("handlingOutcome", Queue.class);

            assertThat(handlingOrder.poll()).isEqualTo("early-[" + testEvent + "]");
            assertThat(handlingOrder.poll()).isEqualTo("late-[" + testEvent + "]");
            assertThat(handlingOrder.poll()).isEqualTo("unordered-[" + testEvent + "]");
        });
    }

    @Test
    void customSpringAxonConfigurationOvertakesDefaultSpringAxonConfiguration() {
        testApplicationContext.withUserConfiguration(CustomizedConfigurerContext.class).run(context -> {
            assertThat(context).hasSingleBean(SpringAxonConfiguration.class);

            SpringAxonConfiguration result = context.getBean(SpringAxonConfiguration.class);
            assertThat(result).isInstanceOf(CustomizedConfigurerContext.CustomSpringAxonConfiguration.class);
        });
    }

    @Test
    void customSpringConfigurerOvertakesDefaultSpringConfigurer() {
        testApplicationContext.withUserConfiguration(CustomizedConfigurerContext.class).run(context -> {
            assertThat(context).hasSingleBean(SpringConfigurer.class);

            SpringConfigurer result = context.getBean(SpringConfigurer.class);
            assertThat(result).isInstanceOf(CustomizedConfigurerContext.CustomSpringConfigurer.class);
        });
    }

    @Test
    void configurerModuleRegisteredHandlerEnhancersAreIncluded() {
        testApplicationContext.withUserConfiguration(HandlerEnhancerConfigurerModuleContext.class).run(context -> {
            assertThat(context).hasBean("handlerInvoked")
                               .isNotNull();
            AtomicBoolean handlerInvoked = context.getBean("handlerInvoked", AtomicBoolean.class);

            assertThat(context).hasBean("enhancerInvoked")
                               .isNotNull();
            AtomicBoolean enhancerInvoked = context.getBean("enhancerInvoked", AtomicBoolean.class);

            assertThat(context).hasSingleBean(HandlerEnhancerConfigurerModuleContext.CommandHandlingComponent.class);
            assertThat(handlerInvoked).isFalse();
            assertThat(enhancerInvoked).isTrue();

            context.getBean("commandGateway", CommandGateway.class).send(new Object(), ProcessingContext.NONE);
            assertThat(handlerInvoked).isTrue();
            assertThat(enhancerInvoked).isTrue();
        });
    }

    @Test
    void configurerModulesRegisteredInOrder() {
        List<ConfigurerModule> initOrder = new ArrayList<>();
        ConfigurerModule module1 = new RegisteringOrderedConfigurerModule(initOrder, 1);
        ConfigurerModule module2 = new RegisteringOrderedConfigurerModule(initOrder, -1);
        ConfigurerModule module3 = new SpringOrderedRegisteringOrderedConfigurerModule(initOrder, 1);
        testApplicationContext.withBean("module1", ConfigurerModule.class, () -> module1)
                              .withBean(" module2", ConfigurerModule.class, () -> module2)
                              .withBean(" module3", ConfigurerModule.class, () -> module3)
                              .run(context -> {
                                  assertThat(initOrder).isEqualTo(Arrays.asList(module2, module3, module1));
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

    // We're not returning the result of invoking the stream operations as a simplification for adjusting the mock.
    @SuppressWarnings({"ResultOfMethodCallIgnored", "DataFlowIssue"})
    static class UpcasterContext {

        @Order(1)
        @Bean
        public EventUpcasterChain firstUpcasterChain() {
            return new EventUpcasterChain(rep -> {
                //noinspection RedundantStreamOptionalCall
                rep.map(ier -> ier);
                return rep;
            });
        }

        @Order(2)
        @Bean
        public EventUpcasterChain secondUpcasterChain() {
            return new EventUpcasterChain(rep -> {
                rep.sorted();
                return rep;
            });
        }
    }

    static class EventHandlerOrderingContext {

        @Bean
        public CountDownLatch eventHandlerInvocations() {
            return new CountDownLatch(3);
        }

        @Bean
        public Queue<String> handlingOutcome() {
            return new LinkedList<>();
        }

        @Bean
        @Order(100)
        public LateEventHandler lateEventHandler(CountDownLatch eventHandlerInvocations,
                                                 Queue<String> handlingOutcome) {
            return new LateEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @Bean
        public EarlyEventHandler earlyEventHandler(CountDownLatch eventHandlerInvocations,
                                                   Queue<String> handlingOutcome) {
            return new EarlyEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @Bean
        public UnorderedEventHandler unorderedEventHandler(CountDownLatch eventHandlerInvocations,
                                                           Queue<String> handlingOutcome) {
            return new UnorderedEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @ProcessingGroup("test")
        static class UnorderedEventHandler {

            private final CountDownLatch invocation;
            private final Queue<String> handlingOutcome;

            public UnorderedEventHandler(CountDownLatch invocation, Queue<String> handlingOutcome) {
                this.invocation = invocation;
                this.handlingOutcome = handlingOutcome;
            }

            @EventHandler
            public void on(String event) {
                handlingOutcome.add("unordered-[" + event + "]");
                invocation.countDown();
            }
        }

        @Order(0)
        @ProcessingGroup("test")
        static class EarlyEventHandler {

            private final CountDownLatch invocation;
            private final Queue<String> handlingOutcome;

            public EarlyEventHandler(CountDownLatch invocation, Queue<String> handlingOutcome) {
                this.invocation = invocation;
                this.handlingOutcome = handlingOutcome;
            }

            @EventHandler
            public void on(String event) {
                handlingOutcome.add("early-[" + event + "]");
                invocation.countDown();
            }
        }

        @ProcessingGroup("test")
        static class LateEventHandler {

            private final CountDownLatch invocation;
            private final Queue<String> handlingOutcome;

            public LateEventHandler(CountDownLatch invocation, Queue<String> handlingOutcome) {
                this.invocation = invocation;
                this.handlingOutcome = handlingOutcome;
            }

            @EventHandler
            public void on(String event) {
                handlingOutcome.add("late-[" + event + "]");
                invocation.countDown();
            }
        }
    }

    static class CustomizedConfigurerContext {

        static class CustomSpringAxonConfiguration extends SpringAxonConfiguration {

            public CustomSpringAxonConfiguration(Configurer configurer) {
                super(configurer);
            }
        }

        @Bean
        public CustomSpringAxonConfiguration customSpringAxonConfiguration(Configurer configurer) {
            return new CustomSpringAxonConfiguration(configurer);
        }

        static class CustomSpringConfigurer extends SpringConfigurer {

            public CustomSpringConfigurer(ConfigurableListableBeanFactory beanFactory) {
                super(beanFactory);
            }
        }

        @Bean
        public CustomSpringConfigurer customSpringConfigurer(ConfigurableListableBeanFactory beanFactory) {
            return new CustomSpringConfigurer(beanFactory);
        }
    }

    static class HandlerEnhancerConfigurerModuleContext {

        @Bean
        public AtomicBoolean handlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        public CommandHandlingComponent commandHandlingComponent(AtomicBoolean handlerInvoked) {
            return new CommandHandlingComponent(handlerInvoked);
        }

        @Bean
        public AtomicBoolean enhancerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        public ConfigurerModule handlerEnhancerConfigurerModule(AtomicBoolean enhancerInvoked) {
            return configurer -> configurer.registerHandlerEnhancerDefinition(c -> new HandlerEnhancerDefinition() {
                @Override
                public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
                    enhancerInvoked.set(true);
                    return original;
                }
            });
        }

        static class CommandHandlingComponent {

            private final AtomicBoolean handlerInvoked;

            public CommandHandlingComponent(AtomicBoolean handlerInvoked) {
                this.handlerInvoked = handlerInvoked;
            }

            @CommandHandler
            public void handle(Object command) {
                handlerInvoked.set(true);
            }
        }
    }

    @Order(-1000)
    private static class SpringOrderedRegisteringOrderedConfigurerModule extends RegisteringOrderedConfigurerModule {

        public SpringOrderedRegisteringOrderedConfigurerModule(List<ConfigurerModule> initOrder, int order) {
            super(initOrder, order);
        }
    }

    private static class RegisteringOrderedConfigurerModule implements ConfigurerModule {

        private final List<ConfigurerModule> initOrder;
        private final int order;

        public RegisteringOrderedConfigurerModule(List<ConfigurerModule> initOrder, int order) {
            this.initOrder = initOrder;
            this.order = order;
        }

        @Override
        public void configureModule(@Nonnull Configurer configurer) {
            initOrder.add(this);
        }

        @Override
        public int order() {
            return order;
        }
    }
}
