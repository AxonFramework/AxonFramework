/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc;

import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.ConfigToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.UnableToClaimTokenException;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.conversion.TestConverter;
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
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JdbcTokenStore}.
 *
 * @author Rene de Waele
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JdbcTokenStoreTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    @jakarta.inject.Named("tokenStore")
    private JdbcTokenStore tokenStore;

    @Autowired
    @jakarta.inject.Named("concurrentTokenStore")
    private JdbcTokenStore concurrentTokenStore;

    @Autowired
    @jakarta.inject.Named("stealingTokenStore")
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
        JdbcTokenEntry.clock = Clock.systemUTC();
    }

    @Test
    void claimAndUpdateToken() {
        ProcessingContext ctx = createProcessingContext();
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments("test", 1, null, ctx))
        );

        transactionManager.executeInTransaction(() -> assertNull(
                joinAndUnwrap(tokenStore.fetchToken("test", 0, null)))
        );
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        transactionManager.executeInTransaction(() -> joinAndUnwrap(tokenStore.storeToken(token, "test", 0, ctx)));
        transactionManager.executeInTransaction(() -> assertEquals(token,
                                                                   joinAndUnwrap(tokenStore.fetchToken(
                                                                           "test",
                                                                           0,
                                                                           null))));
    }

    @Transactional
    @Test
    void updateAndLoadNullToken() {
        ProcessingContext ctx = createProcessingContext();
        joinAndUnwrap(tokenStore.initializeTokenSegments("test", 1, null, ctx));
        joinAndUnwrap(tokenStore.fetchToken("test", 0, null));

        joinAndUnwrap(tokenStore.storeToken(null, "test", 0, ctx));

        TrackingToken token = joinAndUnwrap(tokenStore.fetchToken("test", 0, null));
        assertNull(token);
    }

    @Transactional
    @Test
    void fetchTokenBySegment() {
        List<Segment> createdSegments = transactionManager.fetchInTransaction(() -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                "test",
                2,
                null,
                createProcessingContext())
        ));
        Segment segmentToFetch = createdSegments.get(1);

        transactionManager.executeInTransaction(() -> assertNull(
                joinAndUnwrap(tokenStore.fetchToken("test", segmentToFetch, null))));
    }

    @Transactional
    @Test
    void fetchInitializedNullTokenBySegmentResultsToNull() {
        List<Segment> createdSegments = transactionManager.fetchInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test",
                        1,
                        null,
                        createProcessingContext())
                ));
        Segment segmentToFetch = createdSegments.get(0);

        transactionManager.executeInTransaction(() -> assertNull(
                joinAndUnwrap(tokenStore.fetchToken("test", segmentToFetch, null))));
    }

    @Transactional
    @Test
    void fetchTokenBySegment0FailsDuringMerge() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test",
                        1,
                        null,
                        createProcessingContext())
                ));
        // Create a segment as if there would be two segments in total. This simulates that these two segments have been merged into one.
        Segment segmentToFetch = new Segment(1, 1);
        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> joinAndUnwrap(
                             tokenStore.fetchToken("test", segmentToFetch, null)))
        );
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringMerge() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test",
                        1,
                        null,
                        createProcessingContext())
                ));
        Segment segmentToFetch = new Segment(0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> joinAndUnwrap
                             (tokenStore.fetchToken("test", segmentToFetch, null)))
        );
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringSplit() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test",
                        4,
                        null,
                        createProcessingContext())
                ));
        // Create a segment as if there would be only two segments in total. This simulates that the segments have been split into 4 segments.
        Segment segmentToFetch = new Segment(1, 1);

        assertThrows(UnableToClaimTokenException.class, () -> transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.fetchToken("test", segmentToFetch, null)))
        );
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringSplitSegment0() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test",
                        2,
                        null,
                        createProcessingContext())
                ));
        Segment segmentToFetch = new Segment(0, 0);

        assertThrows(UnableToClaimTokenException.class,
                     () -> transactionManager.executeInTransaction(() -> joinAndUnwrap(
                             tokenStore.fetchToken("test", segmentToFetch, null)))
        );
    }

    @Transactional
    @Test
    void initializeTokenSegmentsResultsToExpectedSegments() {
        List<Segment> createdSegments = joinAndUnwrap(tokenStore.initializeTokenSegments(
                "test1",
                7,
                null,
                createProcessingContext()));

        List<Segment> actual = joinAndUnwrap(tokenStore.fetchSegments("test1", null));

        assertThat(actual).containsExactlyInAnyOrderElementsOf(createdSegments);
    }

    @SuppressWarnings("Duplicates")
    @Transactional
    @Test
    void initializeTokenSegmentsResultsToExpectedTokenAtGivenPositions() {
        List<Segment> createdSegments = joinAndUnwrap(tokenStore.initializeTokenSegments(
                "test1",
                7,
                new GlobalSequenceTrackingToken(10),
                createProcessingContext()));

        List<Segment> actual = joinAndUnwrap(tokenStore.fetchSegments("test1", null));

        assertThat(actual).containsExactlyInAnyOrderElementsOf(createdSegments);

        for (Segment segment : actual) {
            assertEquals(new GlobalSequenceTrackingToken(10),
                         joinAndUnwrap(tokenStore.fetchToken("test1", segment, null)));
        }
    }

    @Transactional
    @Test
    void readingMissingTokenThrowsException() {
        assertThrows(UnableToClaimTokenException.class, () -> joinAndUnwrap(
                tokenStore.fetchToken("test1", 1, null)));
    }

    @Transactional
    @Test
    void fetchSegmentsByProcessorNameResultsToExpectedSegmentCount() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            List<Segment> segments = joinAndUnwrap(tokenStore.fetchSegments("proc1", null));
            assertThat(segments.size(), is(2));
        });
        transactionManager.executeInTransaction(() -> {
            List<Segment> segments = joinAndUnwrap(tokenStore.fetchSegments("proc2", null));
            assertThat(segments.size(), is(2));
        });
        transactionManager.executeInTransaction(() -> {
            List<Segment> segments = joinAndUnwrap(tokenStore.fetchSegments("proc3", null));
            assertThat(segments.size(), is(0));
        });
    }

    @Transactional
    @Test
    void releaseClaimOnSegmentMakesItAvailableForOtherProcessor() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = joinAndUnwrap((
                                                                 concurrentTokenStore.fetchAvailableSegments("proc1",
                                                                                                             null)));
            assertThat(segments.size(), is(0));
            joinAndUnwrap(tokenStore.releaseClaim("proc1", 0, null));
            final List<Segment> segmentsAfterRelease = joinAndUnwrap(
                    concurrentTokenStore.fetchAvailableSegments("proc1", null));
            assertThat(segmentsAfterRelease.size(), is(1));
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = joinAndUnwrap(
                    concurrentTokenStore.fetchAvailableSegments("proc2", null));
            assertThat(segments.size(), is(1));
            joinAndUnwrap(tokenStore.releaseClaim("proc2", 1, null));
            final List<Segment> segmentsAfterRelease = joinAndUnwrap(
                    concurrentTokenStore.fetchAvailableSegments("proc2", null));
            assertThat(segmentsAfterRelease.size(), is(2));
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = joinAndUnwrap(
                    tokenStore.fetchAvailableSegments("proc3", null));
            assertThat(segments.size(), is(0));
        });
    }

    private void prepareTokenStore() {
        var ctx = createProcessingContext();
        transactionManager.executeInTransaction(() -> {
            joinAndUnwrap(tokenStore.initializeTokenSegments("test", 1, null, ctx));
            joinAndUnwrap(tokenStore.initializeTokenSegments("proc1", 2, null, ctx));
            joinAndUnwrap(tokenStore.initializeTokenSegments("proc2", 2, null, ctx));
        });
        transactionManager.executeInTransaction(() -> assertNull(joinAndUnwrap(
                tokenStore.fetchToken("test", 0, null))));

        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.storeToken(
                        new GlobalSequenceTrackingToken(1L),
                        "proc1",
                        0,
                        ctx))
        );
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.storeToken(
                        new GlobalSequenceTrackingToken(2L),
                        "proc1",
                        1,
                        ctx))
        );
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.storeToken(
                        new GlobalSequenceTrackingToken(2L),
                        "proc2",
                        1,
                        ctx))
        );
    }

    @Test
    void claimAndUpdateTokenWithoutTransaction() {
        var ctx = createProcessingContext();
        transactionManager.executeInTransaction(() -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                "test",
                1,
                null,
                ctx)));

        assertNull(joinAndUnwrap(tokenStore.fetchToken("test", 0, null)));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        joinAndUnwrap(tokenStore.storeToken(token, "test", 0, ctx));
        assertEquals(token, joinAndUnwrap(tokenStore.fetchToken("test", 0, null)));
    }

    @Test
    void claimTokenConcurrently() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "concurrent",
                        1,
                        null,
                        createProcessingContext())
                ));

        transactionManager.executeInTransaction(() -> assertNull(
                joinAndUnwrap(tokenStore.fetchToken("concurrent", 0, null))));
        try {
            transactionManager.executeInTransaction(() -> joinAndUnwrap(
                    concurrentTokenStore.fetchToken("concurrent", 0, null)));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void claimTokenConcurrentlyAfterRelease() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "concurrent",
                        1,
                        null,
                        createProcessingContext())
                ));

        transactionManager.executeInTransaction(() -> joinAndUnwrap(
                tokenStore.fetchToken("concurrent", 0, null)));
        transactionManager.executeInTransaction(() -> joinAndUnwrap(
                tokenStore.releaseClaim("concurrent", 0, null)));
        transactionManager.executeInTransaction(() -> assertNull(joinAndUnwrap(concurrentTokenStore.fetchToken(
                "concurrent",
                0,
                null))));
    }

    @Test
    void claimTokenConcurrentlyAfterTimeLimit() {
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "concurrent",
                        1,
                        null,
                        createProcessingContext())
                ));

        transactionManager.executeInTransaction(() -> joinAndUnwrap(
                tokenStore.fetchToken("concurrent", 0, null)));
        JdbcTokenEntry.clock = Clock.offset(Clock.systemUTC(), Duration.ofHours(1));
        transactionManager.executeInTransaction(() -> assertNull(joinAndUnwrap(
                concurrentTokenStore.fetchToken("concurrent", 0, null))));
    }

    @Test
    void stealToken() {
        var ctx = createProcessingContext();
        transactionManager.executeInTransaction(() -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                "stealing",
                1,
                null,
                ctx)));

        transactionManager.executeInTransaction(() -> assertNull(joinAndUnwrap(
                tokenStore.fetchToken("stealing", 0, null)
        )));
        transactionManager.executeInTransaction(() -> assertNull(joinAndUnwrap(
                stealingTokenStore.fetchToken("stealing", 0, null)
        )));

        try {
            transactionManager.executeInTransaction(
                    () -> joinAndUnwrap(tokenStore.storeToken(
                            new GlobalSequenceTrackingToken(0),
                            "stealing",
                            0,
                            ctx)));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("stealing",
                                                                              0,
                                                                              null));
        // claim should still be on stealingTokenStore:
        transactionManager.executeInTransaction(
                () -> joinAndUnwrap(stealingTokenStore.storeToken(
                        new GlobalSequenceTrackingToken(1),
                        "stealing",
                        0,
                        ctx))
        );
    }

    @Test
    void storeAndLoadAcrossTransactions() {
        var ctx = createProcessingContext();
        transactionManager.executeInTransaction(() -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                "multi",
                1,
                null,
                ctx)));

        transactionManager.executeInTransaction(() -> {
            joinAndUnwrap(tokenStore.fetchToken("multi", 0, null));
            joinAndUnwrap(tokenStore.storeToken(
                    new GlobalSequenceTrackingToken(1),
                    "multi",
                    0,
                    ctx));
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = joinAndUnwrap(tokenStore.fetchToken("multi",
                                                                       0,
                                                                       null));
            assertEquals(new GlobalSequenceTrackingToken(1), actual);
            joinAndUnwrap(tokenStore.storeToken(
                    new GlobalSequenceTrackingToken(2),
                    "multi",
                    0,
                    ctx));
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = joinAndUnwrap(tokenStore.fetchToken("multi",
                                                                       0,
                                                                       null));
            assertEquals(new GlobalSequenceTrackingToken(2), actual);
        });
    }

    @Test
    void claimAndDeleteToken() {
        List<Segment> createdSegments = transactionManager.fetchInTransaction(
                () -> joinAndUnwrap(tokenStore.initializeTokenSegments(
                        "test1",
                        2,
                        null,
                        createProcessingContext())
                ));

        // These claim the tokens, despite the method name
        joinAndUnwrap(tokenStore.fetchToken("test1", 0, null));
        joinAndUnwrap(tokenStore.fetchToken("test1", 1, null));

        joinAndUnwrap(tokenStore.deleteToken("test1", 1, null));

        List<Segment> segments = joinAndUnwrap(tokenStore.fetchSegments("test1", null));

        assertThat(segments).containsExactlyInAnyOrder(createdSegments.get(0));
    }

    @Test
    void fetchUnclaimedTokenFails() {
        assertThrows(UnableToClaimTokenException.class, () -> joinAndUnwrap(
                tokenStore.fetchToken("test1", 1, null)));
    }

    @Transactional
    @Test
    void fetchTokenFailsWhenClaimedByOtherNode() {
        assertThrows(UnableToClaimTokenException.class,
                     () -> joinAndUnwrap(concurrentTokenStore.fetchToken("test1",
                                                                         1,
                                                                         null)));
    }

    @Transactional
    @Test
    void identifierInitializedOnDemand() {
        String id1 = joinAndUnwrap(tokenStore.retrieveStorageIdentifier(mock()));
        assertNotNull(id1);
        String id2 = joinAndUnwrap(tokenStore.retrieveStorageIdentifier(mock()));
        assertNotNull(id2);
        assertEquals(id1, id2);
    }

    @Transactional
    @Test
    void identifierReadIfAvailable() throws SQLException {
        ConfigToken token = new ConfigToken(Collections.singletonMap("id", "test123"));
        PreparedStatement ps = dataSource.getConnection()
                                         .prepareStatement(
                                                 "INSERT INTO TokenEntry(processorName, segment, mask, tokenType, token) VALUES(?, ?, ?, ?, ?)");
        ps.setString(1, "__config");
        ps.setInt(2, 0);
        ps.setInt(3, 0);
        ps.setString(4, ConfigToken.class.getName());
        ps.setBytes(5, tokenStore.converter().convert(token, byte[].class));
        ps.executeUpdate();

        String id1 = joinAndUnwrap(tokenStore.retrieveStorageIdentifier(mock()));
        assertNotNull(id1);
        String id2 = joinAndUnwrap(tokenStore.retrieveStorageIdentifier(mock()));
        assertNotNull(id2);
        assertEquals(id1, id2);

        assertEquals("test123", id1);
    }

    private ProcessingContext createProcessingContext() {
        return new StubProcessingContext();
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
            return new JdbcTokenStore(dataSource::getConnection,
                                      TestConverter.JACKSON.getConverter(),
                                      JdbcTokenStoreConfiguration.DEFAULT);
        }

        @Bean
        public JdbcTokenStore concurrentTokenStore(DataSource dataSource) {
            var config = JdbcTokenStoreConfiguration.DEFAULT
                    .claimTimeout(Duration.ofSeconds(2))
                    .nodeId("concurrent");
            return new JdbcTokenStore(dataSource::getConnection,
                                      TestConverter.JACKSON.getConverter(),
                                      config);
        }

        @Bean
        public JdbcTokenStore stealingTokenStore(DataSource dataSource) {
            var config = JdbcTokenStoreConfiguration.DEFAULT
                    .claimTimeout(Duration.ofSeconds(-1))
                    .nodeId("stealing");
            return new JdbcTokenStore(dataSource::getConnection,
                                      TestConverter.JACKSON.getConverter(),
                                      config);
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