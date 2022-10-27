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

import org.axonframework.config.Configuration;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.spring.config.MessageHandlerLookup;
import org.axonframework.spring.config.SpringAggregateLookup;
import org.axonframework.spring.config.SpringAxonConfiguration;
import org.axonframework.spring.config.SpringConfigurer;
import org.axonframework.spring.config.SpringSagaLookup;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

            InOrder upcasterOrder = inOrder(mockStream);
            //noinspection ResultOfMethodCallIgnored
            upcasterOrder.verify(mockStream).map(any());
            //noinspection ResultOfMethodCallIgnored
            upcasterOrder.verify(mockStream).sorted();
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    static class DefaultContext {

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
}
