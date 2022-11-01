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

package org.axonframework.springboot.autoconfig;

import com.thoughtworks.xstream.XStream;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.core.annotation.Order;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

            assertThat(context).getBean("handlingOutcome", Set.class)
                               .isNotNull();
            //noinspection unchecked
            Set<String> handlingOrder = context.getBean("handlingOutcome", Set.class);
            InOrder order = inOrder(handlingOrder);
            order.verify(handlingOrder).add("early-[" + testEvent + "]");
            order.verify(handlingOrder).add("late-[" + testEvent + "]");
            order.verify(handlingOrder).add("unordered-[" + testEvent + "]");
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
    @SuppressWarnings("ResultOfMethodCallIgnored")
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
        public Set<String> handlingOutcome() {
            return spy(new HashSet<>());
        }

        @Bean
        @Order(100)
        public LateEventHandler lateEventHandler(CountDownLatch eventHandlerInvocations, Set<String> handlingOutcome) {
            return new LateEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @Bean
        public EarlyEventHandler earlyEventHandler(CountDownLatch eventHandlerInvocations,
                                                   Set<String> handlingOutcome) {
            return new EarlyEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @Bean
        public UnorderedEventHandler unorderedEventHandler(CountDownLatch eventHandlerInvocations,
                                                           Set<String> handlingOutcome) {
            return new UnorderedEventHandler(eventHandlerInvocations, handlingOutcome);
        }

        @ProcessingGroup("test")
        static class UnorderedEventHandler {

            private final CountDownLatch invocation;
            private final Set<String> handlingOutcome;

            public UnorderedEventHandler(CountDownLatch invocation, Set<String> handlingOutcome) {
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
            private final Set<String> handlingOutcome;

            public EarlyEventHandler(CountDownLatch invocation, Set<String> handlingOutcome) {
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
            private final Set<String> handlingOutcome;

            public LateEventHandler(CountDownLatch invocation, Set<String> handlingOutcome) {
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
}
