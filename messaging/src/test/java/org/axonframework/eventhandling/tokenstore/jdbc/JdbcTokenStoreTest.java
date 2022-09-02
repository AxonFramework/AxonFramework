/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.eventhandling.tokenstore.jdbc;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.eventhandling.tokenstore.ConfigToken;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import javax.sql.DataSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JdbcTokenStoreTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Named("tokenStore")
    private JdbcTokenStore tokenStore;

    @Autowired
    @Named("concurrentTokenStore")
    private JdbcTokenStore concurrentTokenStore;

    @Autowired
    @Named("stealingTokenStore")
    private JdbcTokenStore stealingTokenStore;

    @Autowired
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager.executeInTransaction(() -> {
            try {
                dataSource.getConnection().prepareStatement("DROP TABLE IF EXISTS TokenEntry").executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to drop or create token table", e);
            }
            tokenStore.createSchema(GenericTokenTableFactory.INSTANCE);
        });
    }

    @AfterEach
    void tearDown() {
        AbstractTokenEntry.clock = Clock.systemUTC();
    }

    @Test
    void testClaimAndUpdateToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));

        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("test", 0)));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        transactionManager.executeInTransaction(() -> tokenStore.storeToken(token, "test", 0));
        transactionManager.executeInTransaction(() -> assertEquals(token, tokenStore.fetchToken("test", 0)));
    }

    @Transactional
    @Test
    void testUpdateAndLoadNullToken() {
        tokenStore.initializeTokenSegments("test", 1);
        tokenStore.fetchToken("test", 0);

        tokenStore.storeToken(null, "test", 0);

        TrackingToken token = tokenStore.fetchToken("test", 0);
        assertNull(token);
    }

    @Transactional
    @Test
    void testFetchTokenBySegment() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 2));
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("test", segmentToFetch)));
    }

    @Transactional
    @Test
    void testFetchTokenBySegmentSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("test", segmentToFetch)));
    }

    @Transactional
    @Test
    void testFetchTokenBySegmentFailsDuringMerge() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        //Create a segment as if there would be two segments in total. This simulates that these two segments have been merged into one.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> tokenStore.fetchToken("test", segmentToFetch))
        );
    }

    @Transactional
    @Test
    void testFetchTokenBySegmentFailsDuringMergeSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        Segment segmentToFetch = Segment.computeSegment(0, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> tokenStore.fetchToken("test", segmentToFetch))
        );
    }

    @Transactional
    @Test
    void testFetchTokenBySegmentFailsDuringSplit() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 4));
        //Create a segment as if there would be only two segments in total. This simulates that the segments have been split into 4 segments.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> tokenStore.fetchToken("test", segmentToFetch))
        );
    }

    @Transactional
    @Test
    void testFetchTokenBySegmentFailsDuringSplitSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 2));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> tokenStore.fetchToken("test", segmentToFetch))
        );
    }

    @Transactional
    @Test
    void testInitializeTokens() {
        tokenStore.initializeTokenSegments("test1", 7);

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);
    }

    @SuppressWarnings("Duplicates")
    @Transactional
    @Test
    void testInitializeTokensAtGivenPosition() {
        tokenStore.initializeTokenSegments("test1", 7, new GlobalSequenceTrackingToken(10));

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);

        for (int segment : actual) {
            assertEquals(new GlobalSequenceTrackingToken(10), tokenStore.fetchToken("test1", segment));
        }
    }

    @Transactional
    @Test
    void testInitializeTokensWhileAlreadyPresent() {
        assertThrows(UnableToClaimTokenException.class, () -> tokenStore.fetchToken("test1", 1));
    }

    @Transactional
    @Test
    void testQuerySegments() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc1");
            assertThat(segments.length, is(2));
        });
        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc2");
            assertThat(segments.length, is(2));
        });
        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc3");
            assertThat(segments.length, is(0));
        });
    }

    @Transactional
    @Test
    void testQueryAvailableSegments() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = concurrentTokenStore.fetchAvailableSegments("proc1");
            assertThat(segments.size(), is(0));
            tokenStore.releaseClaim("proc1", 0);
            final List<Segment> segmentsAfterRelease = concurrentTokenStore.fetchAvailableSegments("proc1");
            assertThat(segmentsAfterRelease.size(), is(1));
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = concurrentTokenStore.fetchAvailableSegments("proc2");
            assertThat(segments.size(), is(1));
            tokenStore.releaseClaim("proc2", 1);
            final List<Segment> segmentsAfterRelease = concurrentTokenStore.fetchAvailableSegments("proc2");
            assertThat(segmentsAfterRelease.size(), is(2));
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = tokenStore.fetchAvailableSegments("proc3");
            assertThat(segments.size(), is(0));
        });
    }

    private void prepareTokenStore() {
        transactionManager.executeInTransaction(() -> {
            tokenStore.initializeTokenSegments("test", 1);
            tokenStore.initializeTokenSegments("proc1", 2);
            tokenStore.initializeTokenSegments("proc2", 2);
        });
        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("test", 0)));

        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "proc1", 0)
        );
        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc1", 1)
        );
        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc2", 1)
        );
    }


    @Test
    void testClaimAndUpdateTokenWithoutTransaction() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));

        assertNull(tokenStore.fetchToken("test", 0));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, "test", 0);
        assertEquals(token, tokenStore.fetchToken("test", 0));
    }

    @Test
    void testClaimTokenConcurrently() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> assertNull(tokenStore.fetchToken("concurrent", 0)));
        try {
            transactionManager.executeInTransaction(() -> concurrentTokenStore.fetchToken("concurrent", 0));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void testClaimTokenConcurrentlyAfterRelease() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("concurrent", 0));
        transactionManager.executeInTransaction(() -> assertNull(concurrentTokenStore.fetchToken("concurrent", 0)));
    }

    @Test
    void testClaimTokenConcurrentlyAfterTimeLimit() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        AbstractTokenEntry.clock = Clock.offset(Clock.systemUTC(), Duration.ofHours(1));
        transactionManager.executeInTransaction(() -> assertNull(concurrentTokenStore.fetchToken("concurrent", 0)));
    }

    @Test
    void testStealToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("stealing", 1));

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
    void testStoreAndLoadAcrossTransactions() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("multi", 1));

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

    @Test
    void testClaimAndDeleteToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test1", 2));

        tokenStore.fetchToken("test1", 0);
        tokenStore.fetchToken("test1", 1);

        tokenStore.deleteToken("test1", 1);

        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments("test1"));
    }

    @Test
    void testDeleteUnclaimedTokenFails() {
        assertThrows(UnableToClaimTokenException.class, () -> tokenStore.fetchToken("test1", 1));
    }

    @Transactional
    @Test
    void testDeleteTokenFailsWhenClaimedByOtherNode() {
        assertThrows(UnableToClaimTokenException.class, () -> concurrentTokenStore.fetchToken("test1", 1));
    }

    @Transactional
    @Test
    void testIdentifierInitializedOnDemand() {
        Optional<String> id1 = tokenStore.retrieveStorageIdentifier();
        assertTrue(id1.isPresent());
        Optional<String> id2 = tokenStore.retrieveStorageIdentifier();
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());
    }

    @Transactional
    @Test
    void testIdentifierReadIfAvailable() throws SQLException {
        ConfigToken token = new ConfigToken(Collections.singletonMap("id", "test123"));
        PreparedStatement ps = dataSource.getConnection()
                                         .prepareStatement("INSERT INTO TokenEntry(processorName, segment, tokenType, token) VALUES(?, ?, ?, ?)");
        ps.setString(1, "__config");
        ps.setInt(2, 0);
        ps.setString(3, ConfigToken.class.getName());
        ps.setBytes(4, tokenStore.serializer().serialize(token, byte[].class).getData());
        ps.executeUpdate();

        Optional<String> id1 = tokenStore.retrieveStorageIdentifier();
        assertTrue(id1.isPresent());
        Optional<String> id2 = tokenStore.retrieveStorageIdentifier();
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());

        assertEquals("test123", id1.get());
    }

    @Configuration
    public static class Context {

        @SuppressWarnings("Duplicates")
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
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                 .build();
        }

        @Bean
        public JdbcTokenStore concurrentTokenStore(DataSource dataSource) {
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                 .claimTimeout(Duration.ofSeconds(2))
                                 .nodeId("concurrent")
                                 .build();
        }

        @Bean
        public JdbcTokenStore stealingTokenStore(DataSource dataSource) {
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
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
    }
}
