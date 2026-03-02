/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.springboot.messaging.deadletter.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

/**
 * Integration test validating the combination of a {@link JpaSequencedDeadLetterQueue} with Spring JPA context,
 * extending {@link DeadLetteringEventIntegrationTest} to verify full dead-lettering scenarios backed by a
 * JPA/HSQLDB store.
 *
 * @author Mateusz Nowak
 * @since 5.0.2
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringJpaDeadLetteringIntegrationTest.TestContext.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SpringJpaDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    // Must match DeadLetteringEventIntegrationTest.PROCESSING_GROUP (private static final).
    private static final String PROCESSING_GROUP = "problematicProcessingGroup";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        var jacksonConverter = new JacksonConverter();
        var eventConverter = new DelegatingEventConverter(jacksonConverter);

        return JpaSequencedDeadLetterQueue.<EventMessage>builder()
                                          .processingGroup(PROCESSING_GROUP)
                                          .transactionalExecutorProvider(new JpaTransactionalExecutorProvider(entityManagerFactory))
                                          .eventConverter(eventConverter)
                                          .genericConverter(jacksonConverter)
                                          .build();
    }

    /**
     * Builds a {@link UnitOfWorkFactory} that registers a
     * {@link JpaTransactionalExecutorProvider#SUPPLIER_KEY SUPPLIER_KEY} resource on each
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}, similar to how
     * {@link org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager} populates this resource
     * in production.
     * <p>
     * This allows the {@link JpaTransactionalExecutorProvider} to extract the {@link EntityManagerExecutor} from the
     * context when the event processor handles events, exercising the same code path as production.
     */
    @Override
    protected UnitOfWorkFactory buildUnitOfWorkFactory() {
        return new SimpleUnitOfWorkFactory(
                EmptyApplicationContext.INSTANCE,
                config -> config.registerProcessingLifecycleEnhancer(
                        processingLifecycle ->
                                processingLifecycle.runOnPreInvocation(pc -> {
                                    EntityManager em = entityManagerFactory.createEntityManager();
                                    em.getTransaction().begin();
                                    pc.putResource(
                                            JpaTransactionalExecutorProvider.SUPPLIER_KEY,
                                            CachingSupplier.of(() -> new EntityManagerExecutor(() -> em))
                                    );
                                    pc.runOnCommit(p -> {
                                        em.getTransaction().commit();
                                        em.close();
                                    });
                                    pc.onError((p, phase, e) -> {
                                        if (em.getTransaction().isActive()) {
                                            em.getTransaction().rollback();
                                        }
                                        em.close();
                                    });
                                })
                )
        );
    }

    /**
     * Returns a {@link JacksonConverter} since the JPA DLQ serializes event payloads to JSON,
     * so deserialization requires a converter that understands the JSON format.
     */
    @Override
    protected Converter converter() {
        return new JacksonConverter();
    }

    /**
     * Clears stale dead-letter entries from the database before each test method. {@code @DirtiesContext} does not
     * reliably recreate the Spring context for inherited {@code @Nested} test classes, so an explicit truncation
     * ensures each test starts with an empty DLQ table. This mirrors the drop-and-recreate approach used in
     * {@link org.axonframework.messaging.eventhandling.deadletter.jdbc.JdbcDeadLetteringEventIntegrationTest}.
     */
    @SuppressWarnings("SqlDialectInspection")
    @BeforeEach
    void cleanDatabase() {
        EntityManager em = entityManagerFactory.createEntityManager();
        em.getTransaction().begin();
        em.createNativeQuery("DELETE FROM DeadLetterEntry").executeUpdate();
        em.getTransaction().commit();
        em.close();
    }

    @Configuration
    static class TestContext {

        @Bean
        public DataSource dataSource() {
            // Unique DB URL per context ensures test isolation when @DirtiesContext recreates the Spring context.
            String uniqueDbUrl = "jdbc:hsqldb:mem:springjpadlq-" + System.nanoTime();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(uniqueDbUrl, "sa", "");
            dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
            return dataSource;
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
            // Scan for @Entity and @Embeddable classes in the DLQ package rather than using a named persistence unit.
            emfb.setPackagesToScan("org.axonframework.messaging.eventhandling.deadletter.jpa");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
            jpaVendorAdapter.setGenerateDdl(true);
            jpaVendorAdapter.setShowSql(false);

            emfb.setJpaVendorAdapter(jpaVendorAdapter);
            emfb.setDataSource(dataSource);
            return emfb;
        }

        @Bean
        @DependsOn("entityManagerFactory")
        public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                                                        DataSource dataSource) {
            JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
            transactionManager.setDataSource(dataSource);
            return transactionManager;
        }
    }
}
