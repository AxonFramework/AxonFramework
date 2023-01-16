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

package org.axonframework.spring.eventsourcing.benchmark;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.utils.EventStoreTestUtils;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test class validating the insertion order of events for the {@link JpaEventStorageEngine}.
 *
 * @author Rene de Waele
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = JpaStorageEngineInsertionReadOrderTest.TestContext.class)
class JpaStorageEngineInsertionReadOrderTest {

    private static final Logger logger = LoggerFactory.getLogger(JpaStorageEngineInsertionReadOrderTest.class);

    private final Serializer serializer = TestSerializer.XSTREAM.getSerializer();

    @PersistenceContext
    private EntityManager entityManager;
    @Inject
    private PlatformTransactionManager tx;
    private TransactionTemplate txTemplate;

    private BatchingEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(tx);
        testSubject = JpaEventStorageEngine.builder()
                .snapshotSerializer(serializer)
                .eventSerializer(serializer)
                .entityManagerProvider(new SimpleEntityManagerProvider(entityManager))
                .transactionManager(new SpringTransactionManager(tx))
                .build();
    }

    @AfterEach
    void tearDown() {
        txTemplate.execute(ts -> {
            entityManager.createQuery("DELETE FROM DomainEventEntry").executeUpdate();
            return null;
        });
    }

    @Test
    @Timeout(value = 30)
    void insertConcurrentlyAndCheckReadOrder() throws Exception {
        int threadCount = 10;
        int eventsPerThread = 100;
        int inverseRollbackRate = 7;
        int rollbacksPerThread = (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;

        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        List<TrackedEventMessage<?>> readEvents = readEvents(expectedEventCount);
        for (Thread thread : writerThreads) {
            thread.join();
        }

        assertEquals(expectedEventCount, readEvents.size(),
                "The actually read list of events is shorted than the expected value");
    }

    @Test
    @Timeout(value = 10)
    void insertConcurrentlyAndReadUsingBlockingStreams() throws Exception {
        int threadCount = 10;
        int eventsPerThread = 100;
        int inverseRollbackRate = 2;
        int rollbacksPerThread = (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;

        EmbeddedEventStore embeddedEventStore = EmbeddedEventStore.builder().storageEngine(testSubject).build();
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        TrackingEventStream readEvents = embeddedEventStore.openStream(null);

        int counter = 0;
        while (counter < expectedEventCount) {
            if (readEvents.hasNextAvailable()) {
                counter++;
            }
        }
        for (Thread thread : writerThreads) {
            thread.join();
        }

        assertEquals(expectedEventCount, counter,
                "The actually read list of events is shorted than the expected value");
    }

    @Test
    @Timeout(value = 30)
    void insertConcurrentlyAndReadUsingBlockingStreams_SlowConsumer() throws Exception {
        int threadCount = 4;
        int eventsPerThread = 100;
        int inverseRollbackRate = 2;
        int rollbacksPerThread = (eventsPerThread + inverseRollbackRate - 1) / inverseRollbackRate;
        int expectedEventCount = threadCount * eventsPerThread - rollbacksPerThread * threadCount;

        EmbeddedEventStore embeddedEventStore = EmbeddedEventStore.builder()
                .storageEngine(testSubject)
                .cachedEvents(20)
                .fetchDelay(100)
                .cleanupDelay(1000)
                .build();
        Thread[] writerThreads = storeEvents(threadCount, eventsPerThread, inverseRollbackRate);
        TrackingEventStream readEvents = embeddedEventStore.openStream(null);

        int counter = 0;
        while (counter < expectedEventCount) {
            readEvents.nextAvailable();
            counter++;
            logger.info("SLOW_CONSUMER Handling event #[{}]", counter);
            if (counter % 50 == 0) {
                Thread.sleep(200);
            }
        }
        for (Thread thread : writerThreads) {
            thread.join();
        }

        assertEquals(expectedEventCount, counter,
                "The actually read list of events is shorted than the expected value");
    }

    private Thread[] storeEvents(int threadCount, int eventsPerThread, int inverseRollbackRate) {
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    final int s = j;
                    try {
                        txTemplate.execute(ts -> {
                            testSubject.appendEvents(EventStoreTestUtils.createEvent(
                                    EventStoreTestUtils.AGGREGATE, (long) threadIndex * eventsPerThread + s, "Thread" + threadIndex
                            ));
                            if (s % inverseRollbackRate == 0) {
                                throw new RuntimeException("Rolling back on purpose");
                            }
                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                }
            });
            threads[i].start();
        }
        return threads;
    }

    private List<TrackedEventMessage<?>> readEvents(int eventCount) {
        List<TrackedEventMessage<?>> result = new ArrayList<>();
        TrackingToken lastToken = null;
        while (result.size() < eventCount) {
            List<? extends TrackedEventMessage<?>> batch =
                    testSubject.readEvents(lastToken, false).collect(Collectors.toList());
            for (TrackedEventMessage<?> message : batch) {
                result.add(message);
                if (logger.isDebugEnabled()) {
                    logger.debug(message.getPayload() + " / " + ((DomainEventMessage<?>) message).getSequenceNumber() +
                            " => " + message.trackingToken().toString());
                }
                lastToken = message.trackingToken();
            }
        }
        return result;
    }

    @Configuration
    public static class TestContext {

        @Bean
        public ComboPooledDataSource dataSource() throws PropertyVetoException {
            ComboPooledDataSource dataSource = new ComboPooledDataSource();
            dataSource.setDriverClass("org.hsqldb.jdbcDriver");
            dataSource.setJdbcUrl("jdbc:hsqldb:mem:address-book");
            dataSource.setUser("sa");
            dataSource.setMaxPoolSize(50);
            dataSource.setMinPoolSize(1);
            Properties dataSourceProperties = new Properties();
            dataSourceProperties.setProperty("hsqldb.log_size", "0");
            dataSource.setProperties(dataSourceProperties);
            return dataSource;
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
            entityManagerFactory.setPersistenceUnitName("sb3eventStore");

            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            vendorAdapter.setDatabasePlatform("org.hibernate.dialect.HSQLDialect");
            vendorAdapter.setShowSql(false);
            entityManagerFactory.setJpaVendorAdapter(vendorAdapter);

            HashMap<String, Object> jpaProperties = new HashMap<>();
            jpaProperties.put("jakarta.persistence.schema-generation.database.action", "drop-and-create");
            jpaProperties.put("hibernate.id.new_generator_mappings", true);
            entityManagerFactory.setJpaPropertyMap(jpaProperties);

            entityManagerFactory.setDataSource(dataSource);

            return entityManagerFactory;
        }

        @Bean
        public JpaTransactionManager transactionManager() {
            return new JpaTransactionManager();
        }

        @Bean
        public PersistenceAnnotationBeanPostProcessor persistenceAnnotationBeanPostProcessor() {
            return new PersistenceAnnotationBeanPostProcessor();
        }
    }
}
