/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the {@link TrackingEventProcessor}.
 *
 * @author Allard Buijze
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        AxonServerAutoConfiguration.class})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class TrackingEventProcessorIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private EventBus eventBus;
    @Autowired
    private TransactionManager transactionManager;
    @Autowired
    private CountDownLatch countDownLatch1;
    @Autowired
    private CountDownLatch countDownLatch2;
    @Autowired
    private AtomicReference<String> resetTriggeredReference;
    @Autowired
    private EventProcessingModule eventProcessingModule;
    @Autowired
    private TokenStore tokenStore;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testPublishSomeEvents() throws InterruptedException {
        publishEvent("test1", "test2");
        transactionManager.executeInTransaction(() -> {
            entityManager.createQuery("DELETE FROM TokenEntry t").executeUpdate();
            tokenStore.initializeTokenSegments("first", 1);
            tokenStore.initializeTokenSegments("second", 1);
            tokenStore.storeToken(GapAwareTrackingToken.newInstance(1, new TreeSet<>(Collections.singleton(0L))),
                                  "first",
                                  0);
            tokenStore.storeToken(GapAwareTrackingToken.newInstance(0, new TreeSet<>()), "second", 0);
        });

        assertFalse(countDownLatch1.await(1, TimeUnit.SECONDS));
        publishEvent("test3");
        publishEvent("test4");
        assertTrue(countDownLatch1.await(2, TimeUnit.SECONDS), "Expected all 4 events to have been delivered");
        assertTrue(countDownLatch2.await(2, TimeUnit.SECONDS), "Expected all 4 events to have been delivered");

        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertFalse(((TrackingEventProcessor) ep).isError()));

        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertFalse(
                                     ((TrackingEventProcessor) ep).isError(), "Processor ended with error"
                             ));
    }

    @Test
    void testResetHandlerIsCalledOnResetTokens() {
        String resetContext = "reset-context";

        Optional<TrackingEventProcessor> optionalSecondTep =
                eventProcessingModule.eventProcessor("second", TrackingEventProcessor.class);
        assertTrue(optionalSecondTep.isPresent());

        TrackingEventProcessor secondTep = optionalSecondTep.get();
        secondTep.shutDown();
        secondTep.resetTokens(resetContext);

        assertEquals(resetContext, resetTriggeredReference.get());
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

        @Bean
        public AtomicReference<String> resetTriggeredReference() {
            return new AtomicReference<>();
        }

        @Bean
        @Primary
        public Serializer serializer() {
            return TestSerializer.secureXStreamSerializer();
        }
    }

    @Component
    @ProcessingGroup("first")
    public static class FirstHandler {

        @Autowired
        private CountDownLatch countDownLatch1;

        @SuppressWarnings("unused")
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
        @Autowired
        private AtomicReference<String> resetTriggeredReference;

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(String event) {
            countDownLatch2.countDown();
        }

        @SuppressWarnings("unused")
        @ResetHandler
        public void reset(String resetContext) {
            resetTriggeredReference.set(resetContext);
        }
    }
}
