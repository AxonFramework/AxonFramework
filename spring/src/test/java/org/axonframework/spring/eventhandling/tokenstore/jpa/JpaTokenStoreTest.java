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

package org.axonframework.spring.eventhandling.tokenstore.jpa;

import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.common.legacyjpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.jpa.TokenEntry;
import org.axonframework.eventhandling.tokenstore.legacyjpa.JpaTokenStore;
import org.axonframework.serialization.TestSerializer;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JpaTokenStoreTest {

    @Autowired
    @Qualifier("jpaTokenStore")
    private JpaTokenStore jpaTokenStore;

    @Autowired
    @Qualifier("stealingJpaTokenStore")
    private JpaTokenStore stealingJpaTokenStore;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Transactional
    @Test
    void stealingFromOtherThreadFailsWithRowLock() throws Exception {
        jpaTokenStore.initializeTokenSegments("processor", 1);

        ExecutorService executor1 = Executors.newSingleThreadExecutor();
        CountDownLatch cdl = new CountDownLatch(1);
        try {
            jpaTokenStore.fetchToken("processor", 0);
            Future<?> result = executor1.submit(() -> {

                DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
                txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionStatus tx = transactionManager.getTransaction(txDef);
                cdl.countDown();
                try {
                    stealingJpaTokenStore.fetchToken("processor", 0);
                } finally {
                    transactionManager.rollback(tx);
                }
            });
            cdl.await();
            try {
                result.get(250, TimeUnit.MILLISECONDS);
                fail("Expected task to time out on the write lock");
            } catch (TimeoutException e) {
                // we expect this;
            }
            assertFalse(result.isDone());

            // we cancel the task
            result.cancel(true);

            // and make sure the token is still owned
            TokenEntry tokenEntry = entityManager.find(TokenEntry.class, new TokenEntry.PK("processor", 0));
            assertEquals("local", tokenEntry.getOwner());
        } finally {
            executor1.shutdown();
        }
    }

    @Configuration
    public static class Context {

        @Bean
        public LocalContainerEntityManagerFactoryBean sessionFactory() {
            LocalContainerEntityManagerFactoryBean sessionFactory = new LocalContainerEntityManagerFactoryBean();
            sessionFactory.setPersistenceProvider(new HibernatePersistenceProvider());
            sessionFactory.setPackagesToScan(TokenEntry.class.getPackage().getName());
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.dialect", new HSQLDialect()));
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.hbm2ddl.auto", "create-drop"));
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.show_sql", "false"));
            sessionFactory.setJpaPropertyMap(Collections.singletonMap("hibernate.connection.url",
                                                                      "jdbc:hsqldb:mem:testdb"));
            return sessionFactory;
        }

        @Bean
        public PlatformTransactionManager txManager() {
            return new JpaTransactionManager();
        }

        @Bean
        public JpaTokenStore jpaTokenStore(EntityManagerProvider entityManagerProvider) {
            return JpaTokenStore.builder()
                                .entityManagerProvider(entityManagerProvider)
                                .serializer(TestSerializer.XSTREAM.getSerializer())
                                .nodeId("local")
                                .build();
        }

        @Bean
        public JpaTokenStore stealingJpaTokenStore(EntityManagerProvider entityManagerProvider) {
            return JpaTokenStore.builder()
                                .entityManagerProvider(entityManagerProvider)
                                .serializer(TestSerializer.XSTREAM.getSerializer())
                                .claimTimeout(Duration.ofSeconds(-1))
                                .nodeId("stealing")
                                .build();
        }

        @Bean
        public TransactionManager transactionManager(PlatformTransactionManager txManager) {
            //noinspection Duplicates
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

        @Configuration
        public static class PersistenceConfig {

            @PersistenceContext
            private EntityManager entityManager;

            @Bean
            public EntityManagerProvider entityManagerProvider() {
                return new SimpleEntityManagerProvider(entityManager);
            }
        }
    }
}
