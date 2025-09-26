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

package org.axonframework.eventhandling.processors.streaming.token.store.jpa;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.ConfigToken;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToClaimTokenException;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JpaTokenStore}.
 *
 * @author Rene de Waele
 */
class JpaTokenStoreTest {

    private final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("tokenstore");
    private final EntityManager entityManager = entityManagerFactory.createEntityManager();
    private final EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);

    private final JpaTokenStore jpaTokenStore = getTokenStore("local", null);
    private final JpaTokenStore concurrentJpaTokenStore = getTokenStore("concurrent", Duration.ofSeconds(2));
    private final JpaTokenStore stealingJpaTokenStore = getTokenStore("stealing", Duration.ofSeconds(-1));

    private EntityTransaction transaction;

    @BeforeEach
    public void setUp() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Test
    void updateNullToken() {
        var ctx = createProcessingContext();
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("test", 1, null, ctx));
        joinAndUnwrap(jpaTokenStore.fetchToken("test", 0, null));
        joinAndUnwrap(jpaTokenStore.storeToken(null, "test", 0, ctx));
        List<TokenEntry> tokens = entityManager.createQuery(
                                                       "SELECT t FROM TokenEntry t " +
                                                               "WHERE t.processorName = :processorName",
                                                       TokenEntry.class)
                                               .setParameter(
                                                       "processorName",
                                                       "test")
                                               .getResultList();
        assertEquals(1, tokens.size());
        assertNotNull(tokens.getFirst().getOwner());
        assertNull(tokens.getFirst().getToken(TestSerializer.JACKSON.getSerializer()));
    }

    @Test
    void updateAndLoadNullToken() {
        var ctx = createProcessingContext();
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("test", 1, null, ctx));
        joinAndUnwrap(jpaTokenStore.fetchToken("test", 0, null));
        entityManager.flush();
        joinAndUnwrap(jpaTokenStore.storeToken(null, "test", 0, ctx));
        entityManager.flush();
        entityManager.clear();
        TrackingToken token = joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                                     0,
                                                                     null));
        assertNull(token);
    }

    @Test
    void identifierInitializedOnDemand() {
        Optional<String> id1 = joinAndUnwrap(jpaTokenStore.retrieveStorageIdentifier(mock()));
        assertTrue(id1.isPresent());
        Optional<String> id2 = joinAndUnwrap(jpaTokenStore.retrieveStorageIdentifier(mock()));
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());
    }

    @Test
    void identifierReadIfAvailable() {
        entityManager.persist(new TokenEntry("__config", 0, new ConfigToken(Collections.singletonMap("id", "test")),
                                             jpaTokenStore.serializer()));
        Optional<String> id1 = joinAndUnwrap(jpaTokenStore.retrieveStorageIdentifier(mock()));
        assertTrue(id1.isPresent());
        Optional<String> id2 = joinAndUnwrap(jpaTokenStore.retrieveStorageIdentifier(mock()));
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());

        assertEquals("test", id1.get());
    }

    @Test
    void customLockMode() {
        EntityManager spyEntityManager = mock(EntityManager.class);

        JpaTokenStore testSubject = JpaTokenStore.builder()
                                                 .serializer(TestSerializer.JACKSON.getSerializer())
                                                 .loadingLockMode(LockModeType.NONE)
                                                 .entityManagerProvider(new SimpleEntityManagerProvider(spyEntityManager))
                                                 .nodeId("test")
                                                 .build();

        try {
            joinAndUnwrap(testSubject.fetchToken("processorName", 1, null));
        } catch (Exception e) {
            // ignore. This fails
        }
        verify(spyEntityManager).find(eq(TokenEntry.class),
                                      any(),
                                      eq(LockModeType.NONE));
    }

    @Test
    void initializeTokens() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test1",
                7,
                null,
                createProcessingContext())
        );

        int[] actual = joinAndUnwrap(jpaTokenStore.fetchSegments("test1", null));
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);
    }

    @SuppressWarnings("Duplicates")
    @Test
    void initializeTokensAtGivenPosition() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test1",
                7,
                new GlobalSequenceTrackingToken(10),
                createProcessingContext())
        );

        int[] actual = joinAndUnwrap(jpaTokenStore.fetchSegments("test1", null));
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);

        for (int segment : actual) {
            assertEquals(new GlobalSequenceTrackingToken(10),
                         joinAndUnwrap(jpaTokenStore.fetchToken("test1", segment, null)));
        }
    }

    @Test
    void initializeTokensWhileAlreadyPresent() {
        assertThrows(UnableToClaimTokenException.class, () -> joinAndUnwrap(
                jpaTokenStore.fetchToken("test1", 1, null)));
    }

    @Test
    void deleteTokenRejectedIfNotClaimedOrNotInitialized() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                2,
                null,
                createProcessingContext())
        );

        try {
            joinAndUnwrap(jpaTokenStore.deleteToken("test", 0, null));
            fail("Expected delete to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }

        try {
            joinAndUnwrap(jpaTokenStore.deleteToken("unknown", 0, null));
            fail("Expected delete to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void deleteToken() {
        joinAndUnwrap(jpaTokenStore.initializeSegment(null,
                                                      "delete",
                                                      0,
                                                      null));
        joinAndUnwrap(jpaTokenStore.fetchToken("delete", 0, null));

        entityManager.flush();
        jpaTokenStore.deleteToken("delete", 0, null);

        assertEquals(0L, (long) entityManager.createQuery("SELECT count(t) FROM TokenEntry t " +
                                                                  "WHERE t.processorName = :processorName", Long.class)
                                             .setParameter("processorName", "delete")
                                             .getSingleResult());
    }

    @Test
    void claimAndUpdateToken() {
        var ctx = createProcessingContext();
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("test", 1, null, ctx));

        assertNull(joinAndUnwrap(jpaTokenStore.fetchToken("test", 0, null)));
        joinAndUnwrap(jpaTokenStore.storeToken(
                new GlobalSequenceTrackingToken(1L),
                "test",
                0,
                ctx));

        List<TokenEntry> tokens = entityManager.createQuery("SELECT t FROM TokenEntry t " +
                                                                    "WHERE t.processorName = :processorName",
                                                            TokenEntry.class)
                                               .setParameter("processorName", "test")
                                               .getResultList();
        assertEquals(1, tokens.size());
        assertNotNull(tokens.getFirst().getOwner());
        joinAndUnwrap(jpaTokenStore.releaseClaim("test", 0, null));

        entityManager.flush();
        entityManager.clear();

        TokenEntry token = entityManager.find(TokenEntry.class, new TokenEntry.PK("test", 0));
        assertNull(token.getOwner());
    }

    @Test
    void fetchTokenBySegment() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                2,
                null,
                createProcessingContext()
        ));
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertNull(joinAndUnwrap(jpaTokenStore.fetchToken("test", segmentToFetch, null)));
    }

    @Test
    void fetchTokenBySegmentSegment0() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                1,
                null,
                createProcessingContext()
        ));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertNull(joinAndUnwrap(jpaTokenStore.fetchToken("test", segmentToFetch, null)));
    }

    @Test
    void fetchTokenBySegmentFailsDuringMerge() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                1,
                null,
                createProcessingContext()
        ));
        // Create a segment as if there would be two segments in total. This simulates that these two segments have been merged into one.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                                  segmentToFetch,
                                                                  null))
        );
    }

    @Test
    void fetchTokenBySegmentFailsDuringMergeSegment0() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                1,
                null,
                createProcessingContext()
        ));
        Segment segmentToFetch = Segment.computeSegment(0, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                                  segmentToFetch,
                                                                  null))
        );
    }

    @Test
    void fetchTokenBySegmentFailsDuringSplit() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                4,
                null,
                createProcessingContext()
        ));
        //Create a segment as if there would be only two segments in total. This simulates that the segments have been split into 4 segments.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                                  segmentToFetch,
                                                                  null))
        );
    }

    @Test
    void fetchTokenBySegmentFailsDuringSplitSegment0() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "test",
                2,
                null,
                createProcessingContext()
        ));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertThrows(UnableToClaimTokenException.class,
                     () -> joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                                  segmentToFetch,
                                                                  null))
        );
    }

    @Test
    void querySegments() {
        prepareTokenStore(createProcessingContext());

        {
            final int[] segments = joinAndUnwrap(jpaTokenStore.fetchSegments("proc1",
                                                                             null));
            assertThat(segments.length, is(2));
        }
        {
            final int[] segments = joinAndUnwrap(jpaTokenStore.fetchSegments("proc2",
                                                                             null));
            assertThat(segments.length, is(1));
        }
        {
            final int[] segments = joinAndUnwrap(jpaTokenStore.fetchSegments("proc3",
                                                                             null));
            assertThat(segments.length, is(0));
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void queryAvailableSegments() {
        prepareTokenStore(createProcessingContext());

        {
            final List<Segment> segments = joinAndUnwrap(
                    concurrentJpaTokenStore.fetchAvailableSegments("proc1", null));
            assertThat(segments.size(), is(0));
            joinAndUnwrap(jpaTokenStore.releaseClaim("proc1", 0, null));
            entityManager.flush();
            entityManager.clear();
            final List<Segment> segmentsAfterRelease = joinAndUnwrap(concurrentJpaTokenStore.fetchAvailableSegments(
                    "proc1", null));
            assertThat(segmentsAfterRelease.size(), is(1));
        }
        {
            final List<Segment> segments = joinAndUnwrap(
                    concurrentJpaTokenStore.fetchAvailableSegments("proc2", null));
            assertThat(segments.size(), is(0));
            joinAndUnwrap(jpaTokenStore.releaseClaim("proc2", 0, null));
            entityManager.flush();
            entityManager.clear();
            final List<Segment> segmentsAfterRelease = joinAndUnwrap(concurrentJpaTokenStore.fetchAvailableSegments(
                    "proc2", null));
            assertThat(segmentsAfterRelease.size(), is(1));
        }
        {
            final List<Segment> segments = joinAndUnwrap(
                    jpaTokenStore.fetchAvailableSegments("proc3", null));
            assertThat(segments.size(), is(0));
        }

        entityManager.flush();
        entityManager.clear();
    }

    private void prepareTokenStore(ProcessingContext ctx) {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("test", 1, null, ctx));
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("proc1", 2, null, ctx));
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("proc2", 1, null, ctx));

        assertNull(joinAndUnwrap(jpaTokenStore.fetchToken("test",
                                                          0,
                                                          null)));

        joinAndUnwrap(jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1L),
                                               "proc1",
                                               0,
                                               ctx));
        joinAndUnwrap(jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2L),
                                               "proc1",
                                               1,
                                               ctx));
        joinAndUnwrap(jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2L),
                                               "proc2",
                                               0,
                                               ctx));
    }

    @Test
    void claimTokenConcurrently() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "concurrent",
                1,
                null,
                createProcessingContext()
        ));
        joinAndUnwrap(jpaTokenStore.fetchToken("concurrent", 0, null));
        try {
            joinAndUnwrap(concurrentJpaTokenStore.fetchToken("concurrent",
                                                             0,
                                                             null));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void stealToken() {
        var ctx = createProcessingContext();
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("stealing", 1, null, ctx));

        joinAndUnwrap(jpaTokenStore.fetchToken("stealing", 0, null));
        joinAndUnwrap(stealingJpaTokenStore.fetchToken("stealing", 0, null));

        try {
            joinAndUnwrap(jpaTokenStore.storeToken(
                    new GlobalSequenceTrackingToken(0),
                    "stealing",
                    0,
                    ctx));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        jpaTokenStore.releaseClaim("stealing", 0, null);
        // claim should still be on stealingJpaTokenStore:
        joinAndUnwrap(stealingJpaTokenStore.storeToken(
                new GlobalSequenceTrackingToken(1),
                "stealing",
                0,
                ctx));
    }

    @Test
    void extendingLostClaimFails() {
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments(
                "processor",
                1,
                null,
                createProcessingContext()
        ));
        joinAndUnwrap(jpaTokenStore.fetchToken("processor", 0, null));

        try {
            joinAndUnwrap(stealingJpaTokenStore.extendClaim("processor", 0, null));
            fail("Expected claim extension to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void storeAndLoadAcrossTransactions() {
        var ctx = createProcessingContext();
        joinAndUnwrap(jpaTokenStore.initializeTokenSegments("multi", 1, null, ctx));
        newTransaction();

        joinAndUnwrap(jpaTokenStore.fetchToken("multi", 0, null));
        joinAndUnwrap(jpaTokenStore.storeToken(
                new GlobalSequenceTrackingToken(1),
                "multi",
                0,
                ctx));
        newTransaction();

        TrackingToken actual = joinAndUnwrap(jpaTokenStore.fetchToken("multi",
                                                                      0,
                                                                      null));
        assertEquals(new GlobalSequenceTrackingToken(1), actual);
        joinAndUnwrap(jpaTokenStore.storeToken(
                new GlobalSequenceTrackingToken(2),
                "multi",
                0,
                ctx));
        newTransaction();

        actual = joinAndUnwrap(jpaTokenStore.fetchToken("multi", 0, null));
        assertEquals(new GlobalSequenceTrackingToken(2), actual);
    }


    private ProcessingContext createProcessingContext() {
        return new StubProcessingContext();
    }


    private JpaTokenStore getTokenStore(String nodeId, @Nullable TemporalAmount claimTimeOut) {
        JpaTokenStore.Builder builder = JpaTokenStore.builder()
                                                     .entityManagerProvider(entityManagerProvider)
                                                     .serializer(TestSerializer.JACKSON.getSerializer())
                                                     .nodeId(nodeId);
        if (!Objects.isNull(claimTimeOut)) {
            builder.claimTimeout(claimTimeOut);
        }
        return builder.build();
    }

    private void newTransaction() {
        entityManager.flush();
        entityManager.clear();
        transaction.commit();
        transaction = entityManager.getTransaction();
        transaction.begin();
    }
}