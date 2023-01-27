/*
 * Copyright (c) 2010-2023. Axon Framework
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
        AxonServerBusAutoConfiguration.class,
        AxonServerAutoConfiguration.class,
        AxonServerActuatorAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class TrackingEventProcessorIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private EventBus eventBus;
    @Autowired
    private TransactionManager transactionManager;
    @Autowired
    private EventProcessingModule eventProcessingModule;
    @Autowired
    private TokenStore tokenStore;
    @PersistenceContext
    private EntityManager entityManager;

    private static CountDownLatch countDownLatch1;
    private static CountDownLatch countDownLatch2;
    private static AtomicBoolean resetTriggered;
    private static AtomicReference<String> resetTriggeredWithContext;
    private static Set<TrackedEventMessage<?>> ignoredMessages;

    @BeforeEach
    void setUp() {
        countDownLatch1 = new CountDownLatch(3);
        countDownLatch2 = new CountDownLatch(3);
        resetTriggered = new AtomicBoolean(false);
        resetTriggeredWithContext = new AtomicReference<>();
        ignoredMessages = ConcurrentHashMap.newKeySet();

        eventProcessingModule.eventProcessors().values().forEach(EventProcessor::start);
    }

    @AfterEach
    void tearDown() {
        eventProcessingModule.eventProcessors().values().forEach(EventProcessor::shutDown);

        transactionManager.executeInTransaction(
                () -> {
                    entityManager.createQuery("DELETE FROM TokenEntry t").executeUpdate();
                    entityManager.createQuery("DELETE FROM DomainEventEntry e").executeUpdate();
                }
        );
    }

    @DirtiesContext
    @Test
    public void publishSomeEvents() throws InterruptedException {
        publishEvent(UsedEvent.INSTANCE, UsedEvent.INSTANCE);
        transactionManager.executeInTransaction(() -> {
            entityManager.createQuery("DELETE FROM TokenEntry t").executeUpdate();
            tokenStore.initializeTokenSegments("first", 1);
            tokenStore.initializeTokenSegments("second", 1);
            tokenStore.storeToken(
                    GapAwareTrackingToken.newInstance(1, new TreeSet<>(Collections.singleton(0L))), "first", 0
            );
            tokenStore.storeToken(GapAwareTrackingToken.newInstance(0, new TreeSet<>()), "second", 0);
        });

        assertFalse(countDownLatch1.await(1, TimeUnit.SECONDS));
        publishEvent(UsedEvent.INSTANCE);
        publishEvent(UsedEvent.INSTANCE);
        assertTrue(countDownLatch1.await(2, TimeUnit.SECONDS), "Expected all 4 events to have been delivered");
        assertTrue(countDownLatch2.await(2, TimeUnit.SECONDS), "Expected all 4 events to have been delivered");

        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertFalse(
                                     ep.isError(), "Processor ended with error"
                             ));
    }

    @DirtiesContext
    @Test
    void resetHandlerIsCalledOnResetTokens() {
        String resetContext = "reset-context";

        Optional<TrackingEventProcessor> optionalFirstTep =
                eventProcessingModule.eventProcessor("first", TrackingEventProcessor.class);
        assertTrue(optionalFirstTep.isPresent());

        TrackingEventProcessor firstTep = optionalFirstTep.get();
        firstTep.shutDown();
        firstTep.resetTokens();
        firstTep.start();

        assertTrue(resetTriggered.get());

        Optional<TrackingEventProcessor> optionalSecondTep =
                eventProcessingModule.eventProcessor("second", TrackingEventProcessor.class);
        assertTrue(optionalSecondTep.isPresent());

        TrackingEventProcessor secondTep = optionalSecondTep.get();
        secondTep.shutDown();
        secondTep.resetTokens(resetContext);
        secondTep.start();

        assertEquals(resetContext, resetTriggeredWithContext.get());
    }

    @DirtiesContext
    @Test
    void unhandledEventsAreFilteredOutOfTheBlockingStream() throws InterruptedException {
        publishEvent(UsedEvent.INSTANCE, UnusedEvent.INSTANCE, UsedEvent.INSTANCE, UsedEvent.INSTANCE);

        assertTrue(countDownLatch1.await(10, TimeUnit.SECONDS));

        Set<Class<?>> ignoredClasses = ignoredMessages.stream()
                                                      .map(TrackedEventMessage::getPayloadType)
                                                      .collect(Collectors.toSet());

        assertFalse(ignoredClasses.contains(UsedEvent.class));
        assertTrue(ignoredClasses.contains(UnusedEvent.class));
    }

    private void publishEvent(Object... events) {
        DefaultUnitOfWork.startAndGet(null).execute(
                () -> {
                    Transaction tx = transactionManager.startTransaction();
                    CurrentUnitOfWork.get().onRollback(u -> tx.rollback());
                    CurrentUnitOfWork.get().onCommit(u -> tx.commit());
                    for (Object event : events) {
                        eventBus.publish(asEventMessage(event));
                    }
                });
    }

    @Configuration
    public static class Context {

        @Bean
        @Primary
        public Serializer serializer() {
            return TestSerializer.xStreamSerializer();
        }

        @Autowired
        public void configureCustomStreamableMessageSource(EventProcessingConfigurer configurer) {
            configurer.configureDefaultStreamableMessageSource(config -> trackingToken -> new FilteringBlockingStream(
                    config.eventStore().openStream(trackingToken), ignoredMessages
            ));
        }
    }

    private static class FilteringBlockingStream implements BlockingStream<TrackedEventMessage<?>> {

        private final BlockingStream<TrackedEventMessage<?>> delegate;
        private final Set<TrackedEventMessage<?>> ignoredMessages;

        private FilteringBlockingStream(BlockingStream<TrackedEventMessage<?>> delegate,
                                        Set<TrackedEventMessage<?>> ignoredMessages) {
            this.delegate = delegate;
            this.ignoredMessages = ignoredMessages;
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return delegate.peek();
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            return delegate.hasNextAvailable(timeout, unit);
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            return delegate.nextAvailable();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
            ignoredMessages.add(ignoredMessage);
        }
    }

    @SuppressWarnings("unused")
    @Component
    @ProcessingGroup("first")
    public static class FirstHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(UsedEvent event) {
            countDownLatch1.countDown();
        }

        @SuppressWarnings("unused")
        @ResetHandler
        public void reset() {
            resetTriggered.set(true);
        }
    }

    @Component
    @ProcessingGroup("second")
    public static class SecondHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(UsedEvent event) {
            countDownLatch2.countDown();
        }

        @SuppressWarnings("unused")
        @ResetHandler
        public void reset(String resetContext) {
            resetTriggeredWithContext.set(resetContext);
        }
    }

    private static class UsedEvent {

        private static final UsedEvent INSTANCE = new UsedEvent();
    }

    private static class UnusedEvent {

        private static final UnusedEvent INSTANCE = new UnusedEvent();
    }
}
