/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.AbstractEventProcessor;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.axonframework.common.ReflectionUtils.ensureAccessible;
import static org.junit.Assert.*;

@SpringBootTest
@TestPropertySource("classpath:test-processors.application.properties")
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class EventProcessorConfigurationTest {

    @Autowired
    private EventProcessingModule eventProcessingConfiguration;

    @Autowired
    private SequencingPolicy expectedPolicy;

    @Test
    public void testPublishSomeEvents() throws Exception {
        Map<String, EventProcessor> processors = eventProcessingConfiguration.eventProcessors();
        assertEquals(2, processors.size());
        assertEquals(TrackingEventProcessor.class, processors.get("first").getClass());
        MultiEventHandlerInvoker invoker = (MultiEventHandlerInvoker) ensureAccessible(
                AbstractEventProcessor.class.getDeclaredMethod("eventHandlerInvoker")
        ).invoke(processors.get("first"));
        SimpleEventHandlerInvoker simpleEventHandlerInvoker = (SimpleEventHandlerInvoker) invoker.delegates().get(0);
        SequencingPolicy policy = ReflectionUtils.getFieldValue(
                SimpleEventHandlerInvoker.class.getDeclaredField("sequencingPolicy"), simpleEventHandlerInvoker
        );

        assertEquals(expectedPolicy, policy);
    }

    @Configuration
    public static class Context {

        @Bean
        public CountDownLatch countDownLatch1() {
            return new CountDownLatch(3);
        }

        @Bean
        public CountDownLatch countDownLatch2() {
            return new CountDownLatch(3);
        }

        @Bean
        public SequencingPolicy<?> customPolicy() {
            return new FullConcurrencyPolicy();
        }

        @SuppressWarnings("unused")
        @Component
        @ProcessingGroup("first")
        public static class FirstHandler {

            @Autowired
            private CountDownLatch countDownLatch1;

            @EventHandler
            public void handle(String event) {
                countDownLatch1.countDown();
            }
        }

        @SuppressWarnings("unused")
        @Component
        @ProcessingGroup("second")
        public static class SecondHandler {

            @Autowired
            private CountDownLatch countDownLatch2;

            @EventHandler
            public void handle(String event) {
                countDownLatch2.countDown();
            }
        }
    }
}
