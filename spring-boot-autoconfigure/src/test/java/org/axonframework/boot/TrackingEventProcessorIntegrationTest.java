/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot;

import org.axonframework.boot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.boot.autoconfig.AMQPAutoConfiguration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SpringBootTest
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        AMQPAutoConfiguration.class,
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        AxonServerAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class TrackingEventProcessorIntegrationTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private TransactionManager transactionManager;
    @Autowired
    private CountDownLatch countDownLatch1;
    @Autowired
    private CountDownLatch countDownLatch2;
    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;
    @Autowired
    private TokenStore tokenStore;

    @Test
    public void testPublishSomeEvents() throws InterruptedException {
        eventProcessingConfiguration.shutdown();

        publishEvent("test1", "test2");
        transactionManager.executeInTransaction(() -> {
            tokenStore.storeToken(GapAwareTrackingToken.newInstance(1, new TreeSet<>(Collections.singleton(0L))),
                                  "first",
                                  0);
            tokenStore.storeToken(GapAwareTrackingToken.newInstance(0, new TreeSet<>()), "second", 0);
        });

        eventProcessingConfiguration.start();
        assertFalse(countDownLatch1.await(1, TimeUnit.SECONDS));
        publishEvent("test3");
        publishEvent("test4");
        assertTrue("Expected all 4 events to have been delivered", countDownLatch1.await(2, TimeUnit.SECONDS));
        assertTrue("Expected all 4 events to have been delivered", countDownLatch2.await(1, TimeUnit.SECONDS));

        eventProcessingConfiguration.eventProcessors().forEach((name, ep) -> assertFalse(((TrackingEventProcessor) ep)
                                                                                           .isError()));

        eventProcessingConfiguration.shutdown();
        eventProcessingConfiguration.eventProcessors().forEach((name, ep) -> assertFalse("Processor ended with error",
                                                                                         ((TrackingEventProcessor) ep)
                                                                                           .isError()));
    }

    private void publishEvent(String... events) {
        DefaultUnitOfWork.startAndGet(null).execute(
                () -> {
                    Transaction tx = transactionManager.startTransaction();
                    CurrentUnitOfWork.get().onRollback(u -> tx.rollback());
                    CurrentUnitOfWork.get().onCommit(u -> tx.commit());
                    for (String event : events) {
                        eventBus.publish(asEventMessage(event));
                    }
                });
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

        @Autowired
        public void configure(EventProcessingConfiguration eventProcessingConfiguration) {
            eventProcessingConfiguration.usingTrackingProcessors();
        }
    }

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
