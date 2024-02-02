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

package org.axonframework.spring.modeling.command;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = GenericJpaRepositoryIntegrationTest.TestContext.class)
@Transactional
class GenericJpaRepositoryIntegrationTest implements EventMessageHandler {

    private final List<EventMessage> capturedEvents = new ArrayList<>();
    @Autowired
    @Qualifier("simpleRepository")
    private GenericJpaRepository<JpaAggregate> repository;
    @Autowired
    private EventBus eventBus;
    @PersistenceContext
    private EntityManager entityManager;
    private SubscribingEventProcessor eventProcessor;

    @BeforeEach
    void setUp() {
        SimpleEventHandlerInvoker eventHandlerInvoker = SimpleEventHandlerInvoker.builder()
                                                                                 .eventHandlers(this)
                                                                                 .build();
        eventProcessor = SubscribingEventProcessor.builder()
                                                  .name("test")
                                                  .eventHandlerInvoker(eventHandlerInvoker)
                                                  .messageSource(eventBus)
                                                  .build();
        eventProcessor.start();
    }

    @AfterEach
    void tearDown() {
        eventProcessor.shutDown();
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void storeAndLoadNewAggregate() throws Exception {
        UnitOfWork<?> uow = startAndGetUnitOfWork();
        String originalId = repository.newInstance(() -> new JpaAggregate("Hello")).invoke(JpaAggregate::getIdentifier);
        uow.commit();

        entityManager.flush();
        entityManager.clear();
        List<JpaAggregate> results = entityManager.createQuery("SELECT a FROM JpaAggregate a").getResultList();
        assertEquals(1, results.size());
        JpaAggregate aggregate = results.get(0);
        Assertions.assertEquals(originalId, aggregate.getIdentifier());

        uow = startAndGetUnitOfWork();
        Aggregate<JpaAggregate> storedAggregate = repository.load(originalId);
        uow.commit();
        assertEquals(storedAggregate.identifierAsString(), originalId);
        assertTrue(capturedEvents.isEmpty());
    }

    @Test
    void updateAnAggregate() {
        JpaAggregate agg = new JpaAggregate("First message");
        entityManager.persist(agg);
        entityManager.flush();
        entityManager.clear();

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        Aggregate<JpaAggregate> aggregate = repository.load(agg.getIdentifier());
        aggregate.execute(r -> r.setMessage("And again"));
        aggregate.execute(r -> r.setMessage("And more"));
        uow.commit();

        assertEquals((Long) 1L, aggregate.version());
        assertEquals(2, capturedEvents.size());
        assertNotNull(entityManager.find(JpaAggregate.class, aggregate.identifierAsString()));
    }

    @Test
    void deleteAnAggregate() {
        JpaAggregate agg = new JpaAggregate("First message");
        entityManager.persist(agg);
        entityManager.flush();
        entityManager.clear();
        Assertions.assertEquals((Long) 0L, agg.getVersion());

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        Aggregate<JpaAggregate> aggregate = repository.load(agg.getIdentifier());
        aggregate.execute(r -> r.setMessage("And again"));
        aggregate.execute(r -> r.setMessage("And more"));
        aggregate.execute(JpaAggregate::delete);
        uow.commit();
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, capturedEvents.size());
        assertNull(entityManager.find(JpaAggregate.class, aggregate.identifierAsString()));
    }

    @Override
    public Object handleSync(EventMessage event) {
        this.capturedEvents.add(event);
        return null;
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        return DefaultUnitOfWork.startAndGet(null);
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
        public DataSource dataSource() throws PropertyVetoException {
            ComboPooledDataSource dataSource = new ComboPooledDataSource();
            dataSource.setDriverClass("org.hsqldb.jdbcDriver");
            dataSource.setJdbcUrl("jdbc:hsqldb:mem:axontest");
            dataSource.setUser("sa");
            dataSource.setMaxPoolSize(50);
            dataSource.setMinPoolSize(1);
            Properties dataSourceProperties = new Properties();
            dataSourceProperties.setProperty("hsqldb.log_size", "0");
            dataSource.setProperties(dataSourceProperties);
            return dataSource;
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("axonSpringTest");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
            jpaVendorAdapter.setDatabasePlatform("org.hibernate.dialect.HSQLDialect");
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

        @Bean("mockEventBus")
        public EventBus mockEventBus() {
            return Mockito.mock(EventBus.class);
        }

        @Bean
        public EventBus eventBus() {
            return SimpleEventBus.builder().build();
        }

        @Bean("simpleRepository")
        public Repository<JpaAggregate> simpleRepository(EntityManagerProvider entityManagerProvider,
                                                         EventBus eventBus) {
            return GenericJpaRepository.builder(JpaAggregate.class)
                                       .entityManagerProvider(entityManagerProvider)
                                       .eventBus(eventBus)
                                       .build();
        }
    }
}
