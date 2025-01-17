package org.axonframework.integrationtests.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LegacyJpaEventStorageEngineTest.TestContext.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyJpaEventStorageEngineTest extends AggregateBasedStorageEngineTestSuite<LegacyJpaEventStorageEngine> {

    public static final Serializer TEST_SERIALIZER = TestSerializer.JACKSON.getSerializer();

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Autowired
    private EntityManagerProvider entityManagerProvider;

    @Override
    protected LegacyJpaEventStorageEngine buildStorageEngine() {
//        var txDefinition = new DefaultTransactionDefinition();
//        txDefinition.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new LegacyJpaEventStorageEngine(entityManagerProvider,
                                               new SpringTransactionManager(platformTransactionManager),
                                               TEST_SERIALIZER,
                                               TEST_SERIALIZER,
                                               config -> config.persistenceExceptionResolver(new JdbcSQLErrorCodesResolver())
                                                               .explicitFlush(false));
    }

    @Override
    protected long globalSequenceOfEvent(long eventNumber) {
        return eventNumber;
    }

    @Override
    protected TrackingToken trackingTokenOnPosition(long eventNumber) {
        return GapAwareTrackingToken.newInstance(globalSequenceOfEvent(eventNumber), Collections.emptySet());
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return original.withConvertedPayload(p -> TEST_SERIALIZER.convert(p, String.class));
    }

    @Test
    void sourcingFromNonGapAwareTrackingTokenShouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(5)))
        );
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
            var now = Instant.now();
            DriverManagerDataSource driverManagerDataSource
                    = new DriverManagerDataSource("jdbc:hsqldb:file:./data/" + now + "_legacyjpatest",
                                                  "sa",
                                                  "password");
            driverManagerDataSource.setDriverClassName("org.hsqldb.jdbcDriver");
            return driverManagerDataSource;
        }

        @Bean("entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setPersistenceUnitName("integrationtest");

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
    }
}