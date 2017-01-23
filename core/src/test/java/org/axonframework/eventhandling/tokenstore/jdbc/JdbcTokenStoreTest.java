/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jdbc;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;

import static org.junit.Assert.*;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class JdbcTokenStoreTest {

    @Inject
    private DataSource dataSource;

    @Inject
    @Named("tokenStore")
    private JdbcTokenStore tokenStore;

    @Inject
    @Named("concurrentTokenStore")
    private JdbcTokenStore concurrentTokenStore;

    @Inject
    @Named("stealingTokenStore")
    private JdbcTokenStore stealingTokenStore;

    @Inject
    private TransactionManager transactionManager;

    @Before
    public void setUp() throws Exception {
        transactionManager.executeInTransaction(() -> {
            try {
                dataSource.getConnection().prepareStatement("DROP TABLE IF EXISTS TokenEntry").executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to drop or create token table", e);
            }
            tokenStore.createSchema(new GenericTokenTableFactory());
        });
    }

    @After
    public void tearDown() throws Exception {
        AbstractTokenEntry.clock = Clock.systemUTC();
    }

    @Test
    public void testClaimAndUpdateToken() throws Exception {
        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("test", 0)));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        transactionManager.executeInTransaction(() -> tokenStore.storeToken(token, "test", 0));
        transactionManager.executeInTransaction(() -> assertEquals(token, tokenStore.fetchToken("test", 0)));
    }

    @Test
    public void testClaimAndUpdateTokenWithoutTransaction() throws Exception {
        assertNull(tokenStore.fetchToken("test", 0));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, "test", 0);
        assertEquals(token, tokenStore.fetchToken("test", 0));
    }

    @Test
    public void testClaimTokenConcurrently() {
        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("concurrent", 0)));
        try {
            transactionManager.executeInTransaction(() -> concurrentTokenStore.fetchToken("concurrent", 0));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    public void testClaimTokenConcurrentlyAfterRelease() {
        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("concurrent", 0));
        transactionManager.executeInTransaction(() -> assertNull(concurrentTokenStore.fetchToken("concurrent", 0)));
    }

    @Test
    public void testClaimTokenConcurrentlyAfterTimeLimit() {
        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        AbstractTokenEntry.clock = Clock.offset(Clock.systemUTC(), Duration.ofHours(1));
        transactionManager.executeInTransaction(() -> assertNull(concurrentTokenStore.fetchToken("concurrent", 0)));
    }

    @Test
    public void testStealToken() throws Exception {
        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("stealing", 0)));
        transactionManager.executeInTransaction(() -> assertNull(stealingTokenStore.fetchToken("stealing", 0)));

        try {
            transactionManager.executeInTransaction(
                    () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(0), "stealing", 0));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("stealing", 0));
        // claim should still be on stealingTokenStore:
        transactionManager.executeInTransaction(
                () -> stealingTokenStore.storeToken(new GlobalSequenceTrackingToken(1), "stealing", 0));
    }

    @Test
    public void testStoreAndLoadAcrossTransactions() {
        transactionManager.executeInTransaction(() -> {
            tokenStore.fetchToken("multi", 0);
            tokenStore.storeToken(new GlobalSequenceTrackingToken(1), "multi", 0);
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = tokenStore.fetchToken("multi", 0);
            assertEquals(new GlobalSequenceTrackingToken(1), actual);
            tokenStore.storeToken(new GlobalSequenceTrackingToken(2), "multi", 0);
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = tokenStore.fetchToken("multi", 0);
            assertEquals(new GlobalSequenceTrackingToken(2), actual);
        });
    }

    @Configuration
    public static class Context {

        @Bean
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:testdb");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public PlatformTransactionManager txManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public JdbcTokenStore tokenStore(DataSource dataSource) {
            return new JdbcTokenStore(dataSource::getConnection, new XStreamSerializer());
        }

        @Bean
        public JdbcTokenStore concurrentTokenStore(DataSource dataSource) {
            return new JdbcTokenStore(dataSource::getConnection, new XStreamSerializer(), new TokenSchema(),
                                      Duration.ofSeconds(2), "concurrent", byte[].class);
        }

        @Bean
        public JdbcTokenStore stealingTokenStore(DataSource dataSource) {
            return new JdbcTokenStore(dataSource::getConnection, new XStreamSerializer(), new TokenSchema(),
                                      Duration.ofSeconds(-1), "stealing", byte[].class);
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
