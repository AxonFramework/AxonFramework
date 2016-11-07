package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.common.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class JpaTokenStoreTest {

    @Autowired
    @Qualifier("jpaTokenStore")
    private JpaTokenStore jpaTokenStore;

    @Autowired
    @Qualifier("concurrentJpaTokenStore")
    private JpaTokenStore concurrentJpaTokenStore;

    @Autowired
    @Qualifier("stealingJpaTokenStore")
    private JpaTokenStore stealingJpaTokenStore;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate txTemplate;

    @Before
    public void setUp() throws Exception {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    @Test
    public void testClaimAndUpdateToken() throws Exception {
        assertNull(jpaTokenStore.fetchToken("test", 0));
        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);

        List<TokenEntry> tokens = entityManager.createQuery("SELECT t FROM TokenEntry t " +
                                                                    "WHERE t.processorName = :processorName",
                                                            TokenEntry.class)
                .setParameter("processorName", "test")
                .getResultList();
        assertEquals(1, tokens.size());
        assertNotNull(tokens.get(0).getOwner());
        jpaTokenStore.releaseClaim("test", 0);

        TokenEntry token = entityManager.find(TokenEntry.class, new TokenEntry.PK("test", 0));
        assertNull(token.getOwner());
    }

    @Transactional
    @Test
    public void testClaimTokenConcurrently() throws Exception {
        jpaTokenStore.fetchToken("concurrent", 0);
        try {
            concurrentJpaTokenStore.fetchToken("concurrent", 0);
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Transactional
    @Test
    public void testStealToken() throws Exception {
        jpaTokenStore.fetchToken("stealing", 0);
        stealingJpaTokenStore.fetchToken("stealing", 0);

        try {
            jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(0), "stealing", 0);
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        jpaTokenStore.releaseClaim("stealing", 0);
        // claim should still be on stealingJpaTokenStore:
        stealingJpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1), "stealing", 0);
    }

    @Test
    public void testStoreAndLoadAcrossTransactions() {
        txTemplate.execute(status -> {
            jpaTokenStore.fetchToken("multi", 0);
            jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1), "multi", 0);
            return null;
        });

        txTemplate.execute(status -> {
            TrackingToken actual = jpaTokenStore.fetchToken("multi", 0);
            assertEquals(new GlobalSequenceTrackingToken(1), actual);
            jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2), "multi", 0);
            return null;
        });

        txTemplate.execute(status -> {
            TrackingToken actual = jpaTokenStore.fetchToken("multi", 0);
            assertEquals(new GlobalSequenceTrackingToken(2), actual);
            return null;
        });
    }

    @Configuration
    public static class Context {

        @Bean
        public EntityManagerProvider entityManagerProvider() {
            return new ContainerManagedEntityManagerProvider();
        }

        @Bean
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:testdb");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean sessionFactory() {
            LocalContainerEntityManagerFactoryBean sessionFactory = new LocalContainerEntityManagerFactoryBean();
            sessionFactory.setPersistenceProvider(new HibernatePersistenceProvider());
            sessionFactory.setPackagesToScan(TokenEntry.class.getPackage().getName());
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.dialect", new HSQLDialect()));
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.hbm2ddl.auto", "create-drop"));
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.connection.url", "jdbc:hsqldb:mem:testdb"));
            return sessionFactory;
        }

        @Bean
        public PlatformTransactionManager txManager() {
            return new JpaTransactionManager();
        }

        @Bean
        public JpaTokenStore jpaTokenStore(EntityManagerProvider entityManagerProvider) {
            return new JpaTokenStore(entityManagerProvider, new XStreamSerializer());
        }

        @Bean
        public JpaTokenStore concurrentJpaTokenStore(EntityManagerProvider entityManagerProvider) {
            return new JpaTokenStore(entityManagerProvider, new XStreamSerializer(), Duration.ofSeconds(2),
                                     "concurrent");
        }

        @Bean
        public JpaTokenStore stealingJpaTokenStore(EntityManagerProvider entityManagerProvider) {
            return new JpaTokenStore(entityManagerProvider, new XStreamSerializer(), Duration.ofSeconds(-1),
                                     "stealing");
        }

        @Bean
        public TransactionManager transactionManager(PlatformTransactionManager txManager) {
            return () -> {
                TransactionStatus transaction = txManager.getTransaction(new DefaultTransactionDefinition());
                return new Transaction() {
                    @Override
                    public void commit() {
                        txManager.commit(transaction);
                    }

                    @Override
                    public void rollback() {
                        txManager.rollback(transaction);
                    }
                };
            };
        }
    }

}
