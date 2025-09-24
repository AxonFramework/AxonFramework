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

package org.axonframework.springboot;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorsConfigurer;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.subscribing.SubscribingEventProcessorModule;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.spring.config.SpringComponentRegistry;
import org.axonframework.springboot.fixture.event.test1.FirstHandler;
import org.axonframework.springboot.fixture.event.test2.Test2EventHandlingConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating configuration through a properties file, adjusting the {@link EventProcessorProperties}.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 */
class EventProcessorConfigurationTest {


    private static final String KEY1 = "org.axonframework.springboot.fixture.event.test1";
    private static final String KEY2 = "org.axonframework.springboot.fixture.event.test2";

    @Nested
    @SpringBootTest(
            classes = {EventProcessorConfigurationTest.MyCustomContext.class},
            properties = {
                    "axon.axonserver.enabled=false",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].mode=pooled",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].initialSegmentCount=73",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].tokenClaimInterval=7",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].tokenClaimIntervalTimeUnit=SECONDS",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].threadCount=5",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].batchSize=3"
            }
    )
    class TwoPoolProcessorsTest {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private EventProcessorProperties properties;

        @Test
        void processorConfigurationWithCustomValues() throws Exception {


            assertThat(properties.getProcessors().get(KEY1)).isNotNull();

            assertThat(context.getBean(SpringComponentRegistry.class)).isNotNull();
            Map<String, Module> modules = ReflectionUtils.getFieldValue(
                    SpringComponentRegistry.class.getDeclaredField("modules"),
                    context.getBean(SpringComponentRegistry.class)
            );
            assertThat(modules).isNotNull();
            assertThat(modules).hasSize(4);
            var module1 = modules.get("EventProcessor[" + KEY1 + "]");
            assertThat(module1).isNotNull();
            assertThat(module1).isInstanceOf(PooledStreamingEventProcessorModule.class);

            var module2 = modules.get("EventProcessor[" + KEY2 + "]");
            assertThat(module2).isNotNull();
            assertThat(module2).isInstanceOf(PooledStreamingEventProcessorModule.class);
        }
    }

    @Nested
    @SpringBootTest(
            classes = {EventProcessorConfigurationTest.MyCustomContext.class},
            properties = {
                    "axon.axonserver.enabled=false",
                    "axon.eventhandling.processors[org.axonframework.springboot.fixture.event.test1].mode=subscribing",
            }
    )
    class PooledAndSubscribingProcessorTest {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private EventProcessorProperties properties;

        @Test
        void processorConfigurationSubscribed() throws Exception {

            var settings = properties.getProcessors().get(KEY1);
            assertThat(settings).isNotNull();
            assertThat(settings.getMode()).isEqualTo(EventProcessorProperties.Mode.SUBSCRIBING);

            assertThat(context.getBean(SpringComponentRegistry.class)).isNotNull();
            Map<String, Module> modules = ReflectionUtils.getFieldValue(
                    SpringComponentRegistry.class.getDeclaredField("modules"),
                    context.getBean(SpringComponentRegistry.class)
            );
            assertThat(modules).isNotNull();
            assertThat(modules).hasSize(4);

            Map<String, Configuration> modulesConfigurations = ReflectionUtils.getFieldValue(
                    SpringComponentRegistry.class.getDeclaredField("moduleConfigurations"),
                    context.getBean(SpringComponentRegistry.class)
            );

            var ep1 = "EventProcessor[" + KEY1 + "]";
            var ep2 = "EventProcessor[" + KEY2 + "]";

            assertThat(modulesConfigurations).hasSize(2);
            assertThat(modulesConfigurations).containsKeys(ep1, ep2);

            var module1 = modules.get(ep1);
            assertThat(module1).isNotNull();
            assertThat(module1).isInstanceOf(SubscribingEventProcessorModule.class);

            var module2 = modules.get(ep2);
            assertThat(module2).isNotNull();
            assertThat(module2).isInstanceOf(PooledStreamingEventProcessorModule.class);
        }
    }


    @SuppressWarnings("unused")
    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @ComponentScan(basePackageClasses = FirstHandler.class) // explicit scan to a location not in the same sub-package
    @Import(Test2EventHandlingConfiguration.class) // explicit configuration import
    private static class MyCustomContext {

        @MockitoBean
        private SubscribableMessageSource messageSource;

        @MockitoBean(name = "alternativeMessageSource")
        private SubscribableMessageSource alternativeMessageSource;

        @MockitoBean
        private StreamableEventSource eventSource;

        @MockitoBean("alternativeEventStore")
        private StreamableEventSource alternativeEventSource;

        @MockitoBean
        private TokenStore tokenStore;
    }
}
