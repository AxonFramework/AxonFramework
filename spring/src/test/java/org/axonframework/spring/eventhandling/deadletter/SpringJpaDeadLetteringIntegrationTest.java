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

package org.axonframework.spring.eventhandling.deadletter;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.spring.utils.PostgresTestContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the {@link JpaSequencedDeadLetterQueue} with
 * an {@link org.axonframework.eventhandling.EventProcessor} and {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Mitchell Herrijgers
 */

@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:hsqldb.database.properties")
class SpringJpaDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Autowired
    private EntityManagerProvider entityManagerProvider;

    @BeforeAll
    public static void start() {
        PostgresTestContainer.getInstance().start();
    }

    @AfterAll
    public static void stop() {
        PostgresTestContainer.getInstance().stop();
    }

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
        return JpaSequencedDeadLetterQueue.builder()
                                          .processingGroup(PROCESSING_GROUP)
                                          .transactionManager(getTransactionManager())
                                          .entityManagerProvider(entityManagerProvider)
                                          .serializer(TestSerializer.JACKSON.getSerializer())
                                          .build();
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
        public DataSource dataSource(@Value("${jdbc.driverclass}") String driverClass,
                                     @Value("${jdbc.url}") String url,
                                     @Value("${jdbc.username}") String username,
                                     @Value("${jdbc.password}") String password) {
            DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource(url, username, password);
            driverManagerDataSource.setDriverClassName(driverClass);
            return Mockito.spy(driverManagerDataSource);
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(
                @Value("${hibernate.sql.dialect}") String dialect,
                @Value("${hibernate.sql.generateddl}") boolean generateDdl,
                @Value("${hibernate.sql.show}") boolean showSql,
                DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("integrationtest");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
            jpaVendorAdapter.setDatabasePlatform(dialect);
            jpaVendorAdapter.setGenerateDdl(generateDdl);
            jpaVendorAdapter.setShowSql(showSql);
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
