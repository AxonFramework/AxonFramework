package org.axonframework.integrationtests.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LegacyJpaEventStorageEngineTest.TestContext.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // todo: find better way to clear db between tests
class LegacyJpaEventStorageEngineTest extends AggregateBasedStorageEngineTestSuite<LegacyJpaEventStorageEngine> {

    public static final Serializer TEST_SERIALIZER = TestSerializer.JACKSON.getSerializer();
    @Autowired
    private EntityManagerProvider entityManagerProvider;

    @Autowired
    @Qualifier("axonTransactionManager")
    private TransactionManager transactionManager;

    @Override
    protected LegacyJpaEventStorageEngine buildStorageEngine() {
        return new LegacyJpaEventStorageEngine(entityManagerProvider,
                                               transactionManager,
                                               TEST_SERIALIZER,
                                               TEST_SERIALIZER,
                                               config -> config.persistenceExceptionResolver(new JdbcSQLErrorCodesResolver())
                                                               .lowestGlobalSequence(1));
    }

    @Override
    protected long sequenceOfEventNo(long eventNo) {
        return eventNo;
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return original.withConvertedPayload(p -> TEST_SERIALIZER.convert(p, String.class));
    }

    // todo: test batching
    // todo: test token with gaps

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

//        @Bean
//        public DataSource dataSource() {
//            DriverManagerDataSource driverManagerDataSource
//                    = new DriverManagerDataSource("jdbc:tc:postgresql:17.2:///axon_test", "axon", "axon");
//            driverManagerDataSource.setDriverClassName("org.postgresql.Driver");
//            return Mockito.spy(driverManagerDataSource);
//        }

        @Bean
        public DataSource dataSource() {
            var now = Instant.now();
            DriverManagerDataSource driverManagerDataSource
                    = new DriverManagerDataSource("jdbc:hsqldb:file:./data/" + now + "_legacyjpatest",
                                                  "sa",
                                                  "password");
            driverManagerDataSource.setDriverClassName("org.hsqldb.jdbcDriver");
            return Mockito.spy(driverManagerDataSource);
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("integrationtest");

            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
//            jpaVendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
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

        @Bean("axonTransactionManager")
        public TransactionManager axonTransactionManager(PlatformTransactionManager platformTransactionManager) {
            return new SpringTransactionManager(platformTransactionManager);
        }

        @Bean
        public PersistenceAnnotationBeanPostProcessor persistenceAnnotationBeanPostProcessor() {
            return new PersistenceAnnotationBeanPostProcessor();
        }
    }
}