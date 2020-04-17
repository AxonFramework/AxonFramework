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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.interceptors.LoggingInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests customization of the event handlers on a Saga, updating the configuration through an autowired method.
 * Ensures that if there are multiple threads and a logging interceptor events are still received once.
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        AxonServerAutoConfiguration.class})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class SagaCustomizeIntegrationTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private TransactionManager transactionManager;
    @Autowired
    private AtomicInteger eventsReceived;
    @Autowired
    private EventProcessingModule eventProcessingModule;
    @Autowired
    private TokenStore tokenStore;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testPublishSomeEvents() throws InterruptedException {
        publishEvent(new EchoEvent(UUID.randomUUID().toString()));
        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertTrue(((TrackingEventProcessor) ep).isRunning()));

        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertFalse(
                                     ((TrackingEventProcessor) ep).isError(), "Processor ended with error"
                             ));

        Thread.sleep(Duration.ofSeconds(1).toMillis());
        assertEquals(1, eventsReceived.get());
        publishEvent(new EchoEvent(UUID.randomUUID().toString()));
        Thread.sleep(Duration.ofSeconds(1).toMillis());
        assertEquals(2, eventsReceived.get());
    }

    private void publishEvent(EchoEvent... events) {
        DefaultUnitOfWork.startAndGet(null).execute(
                () -> {
                    Transaction tx = transactionManager.startTransaction();
                    CurrentUnitOfWork.get().onRollback(u -> tx.rollback());
                    CurrentUnitOfWork.get().onCommit(u -> tx.commit());
                    for (EchoEvent event : events) {
                        eventBus.publish(asEventMessage(event));
                    }
                });
    }

    @AutoConfigureBefore(AxonAutoConfiguration.class)
    @Configuration
    public static class Context {

        @Bean
        public AtomicInteger eventsReceived() {
            return new AtomicInteger();
        }

        @Autowired
        private void registerEventHandlers(
                EventProcessingModule eventProcessingConfiguration,
                EventStore eventStore) throws ClassNotFoundException {
            Set<String> registeredProcessingGroups = new HashSet<>();
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Saga.class));
            for (BeanDefinition bd : scanner.findCandidateComponents("org.axonframework.springboot")) {
                Class<?> aClass = Class.forName(bd.getBeanClassName());
                String processorGroupName = eventProcessingConfiguration.sagaProcessingGroup(aClass);
                if (!registeredProcessingGroups.contains(processorGroupName)) {
                    StreamableMessageSource<TrackedEventMessage<?>> messageSource = eventStore;
                    eventProcessingConfiguration.registerTrackingEventProcessor(processorGroupName, c -> messageSource,
                                                                                c -> TrackingEventProcessorConfiguration
                                                                                        .forParallelProcessing(2)
                                                                                        .andInitialSegmentsCount(2));

                    eventProcessingConfiguration.registerHandlerInterceptor(processorGroupName,
                                                                            c -> new LoggingInterceptor<>());
                    registeredProcessingGroups.add(processorGroupName);
                }
            }
        }
    }

    @Saga
    public static class SimpleSaga {

        @Autowired
        private AtomicInteger eventsReceived;

        @SagaEventHandler(associationProperty = "id")
        @StartSaga
        public void on(EchoEvent echoEvent) {
            eventsReceived.getAndIncrement();
        }
    }

    public static class EchoEvent {

        private String id;

        public EchoEvent() {
        }

        public EchoEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
