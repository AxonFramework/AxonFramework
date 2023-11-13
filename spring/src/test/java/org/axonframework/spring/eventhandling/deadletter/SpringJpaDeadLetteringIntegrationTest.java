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

package org.axonframework.spring.eventhandling.deadletter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterEntry;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;
import org.axonframework.eventhandling.deadletter.jpa.EventMessageDeadLetterJpaConverter;
import org.axonframework.eventhandling.deadletter.jpa.JpaDeadLetter;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.spring.utils.MysqlTestContainerExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the {@link JpaSequencedDeadLetterQueue}
 * with an {@link org.axonframework.eventhandling.EventProcessor} and {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Mitchell Herrijgers
 */

@ExtendWith(SpringExtension.class)
@ExtendWith(MysqlTestContainerExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SpringJpaDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private EntityManagerProvider entityManagerProvider;
    private final Serializer serializer = TestSerializer.JACKSON.getSerializer();
    private final DeadLetterJpaConverter<EventMessage<?>> converter = new EventMessageDeadLetterJpaConverter();
    private JpaSequencedDeadLetterQueue<EventMessage<?>> jpaDeadLetterQueue;

    @BeforeEach
    public void clear() {
        getTransactionManager().executeInTransaction(() -> {
            entityManagerProvider.getEntityManager().createQuery("delete from DeadLetterEntry e").executeUpdate();
        });
    }

    @Override
    protected TransactionManager getTransactionManager() {
        return new SpringTransactionManager(platformTransactionManager);
    }

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
        jpaDeadLetterQueue = JpaSequencedDeadLetterQueue.builder()
                                                        .processingGroup(PROCESSING_GROUP)
                                                        .entityManagerProvider(entityManagerProvider)
                                                        .transactionManager(getTransactionManager())
                                                        .serializer(serializer)
                                                        .addConverter(converter)
                                                        .build();
        return jpaDeadLetterQueue;
    }

    @Test
    void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
        String aggregateId = UUID.randomUUID().toString();
        Map<Integer, GenericDeadLetter<EventMessage<?>>> insertedLetters = new HashMap<>();

        Iterator<DeadLetter<? extends EventMessage<?>>> resultIterator =
                jpaDeadLetterQueue.deadLetterSequence(aggregateId).iterator();
        assertFalse(resultIterator.hasNext());

        IntStream.range(0, 64)
                 .boxed()
                 .sorted(Collections.reverseOrder())
                 .forEach(i -> {
                     GenericDeadLetter<EventMessage<?>> letter =
                             new GenericDeadLetter<>(aggregateId, asEventMessage(i));
                     insertLetterAtIndex(aggregateId, letter, i);
                     insertedLetters.put(i, letter);
                 });

        resultIterator = jpaDeadLetterQueue.deadLetterSequence(aggregateId).iterator();
        for (Map.Entry<Integer, GenericDeadLetter<EventMessage<?>>> entry : insertedLetters.entrySet()) {
            Integer sequenceIndex = entry.getKey();
            Supplier<String> assertMessageSupplier = () -> "Failed asserting event [" + sequenceIndex + "]";
            assertTrue(resultIterator.hasNext(), assertMessageSupplier);

            GenericDeadLetter<EventMessage<?>> expected = entry.getValue();
            DeadLetter<? extends EventMessage<?>> result = resultIterator.next();
            assertTrue(result instanceof JpaDeadLetter);
            JpaDeadLetter<? extends EventMessage<?>> actual = ((JpaDeadLetter<? extends EventMessage<?>>) result);

            assertEquals(expected.getSequenceIdentifier(), actual.getSequenceIdentifier(), assertMessageSupplier);
            assertEquals(expected.message().getPayload(), actual.message().getPayload(), assertMessageSupplier);
            assertFalse(result.cause().isPresent(), assertMessageSupplier);
            assertEquals(expected.diagnostics(), actual.diagnostics(), assertMessageSupplier);
            assertEquals(sequenceIndex.longValue(), actual.getIndex(), assertMessageSupplier);
        }
    }

    private void insertLetterAtIndex(String aggregateId, DeadLetter<EventMessage<?>> letter, int index) {
        transactionManager.executeInTransaction(() -> {
            DeadLetterEventEntry eventEntry = converter.convert(letter.message(), serializer, serializer);
            DeadLetterEntry deadLetter = new DeadLetterEntry(PROCESSING_GROUP,
                                                             aggregateId,
                                                             index,
                                                             eventEntry,
                                                             letter.enqueuedAt(),
                                                             letter.lastTouched(),
                                                             letter.cause().orElse(null),
                                                             letter.diagnostics(),
                                                             serializer);
            entityManagerProvider.getEntityManager()
                                 .persist(deadLetter);
        });
    }

    @Configuration
    public static class TestContext {

        @Configuration
        public static class PersistenceConfig {

            @PersistenceContext
            private EntityManager entityManager;

            @Bean
            public EntityManagerProvider entityManagerProvider() {
                return new SimpleEntityManagerProvider(entityManager);
            }
        }

        @Bean
        public DataSource dataSource() {
            return MysqlTestContainerExtension.getInstance().asDataSource();
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("axonSpringTest");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
            jpaVendorAdapter.setGenerateDdl(true);
            jpaVendorAdapter.setShowSql(false);
            entityManagerFactoryBean.setJpaVendorAdapter(jpaVendorAdapter);

            entityManagerFactoryBean.setDataSource(dataSource);
            return entityManagerFactoryBean;
        }

        @Bean
        @DependsOn("entityManagerFactory")
        public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                                                        DataSource dataSource) {
            JpaTransactionManager jpaTransactionManager = new JpaTransactionManager(entityManagerFactory);
            jpaTransactionManager.setDataSource(dataSource);
            return jpaTransactionManager;
        }

        @Bean
        public PersistenceAnnotationBeanPostProcessor persistenceAnnotationBeanPostProcessor() {
            return new PersistenceAnnotationBeanPostProcessor();
        }
    }
}
