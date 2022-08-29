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

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.ConfigToken;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    public void cleanup(){
        transaction.commit();
    }

    @Test
    void testUpdateNullToken() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        jpaTokenStore.fetchToken("test", 0);
        jpaTokenStore.storeToken(null, "test", 0);
        List<TokenEntry> tokens = entityManager.createQuery("SELECT t FROM TokenEntry t " +
                                                                    "WHERE t.processorName = :processorName",
                                                            TokenEntry.class)
                                               .setParameter("processorName", "test")
                                               .getResultList();
        assertEquals(1, tokens.size());
        assertNotNull(tokens.get(0).getOwner());
        assertNull(tokens.get(0).getToken(TestSerializer.XSTREAM.getSerializer()));
    }

    @Test
    void testUpdateAndLoadNullToken() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        jpaTokenStore.fetchToken("test", 0);
        entityManager.flush();
        jpaTokenStore.storeToken(null, "test", 0);
        entityManager.flush();
        entityManager.clear();
        TrackingToken token = jpaTokenStore.fetchToken("test", 0);
        assertNull(token);
    }

    @Test
    void testIdentifierInitializedOnDemand() {
        Optional<String> id1 = jpaTokenStore.retrieveStorageIdentifier();
        assertTrue(id1.isPresent());
        Optional<String> id2 = jpaTokenStore.retrieveStorageIdentifier();
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());
    }

    @Test
    void testIdentifierReadIfAvailable() {
        entityManager.persist(new TokenEntry("__config", 0, new ConfigToken(Collections.singletonMap("id", "test")), jpaTokenStore.serializer()));
        Optional<String> id1 = jpaTokenStore.retrieveStorageIdentifier();
        assertTrue(id1.isPresent());
        Optional<String> id2 = jpaTokenStore.retrieveStorageIdentifier();
        assertTrue(id2.isPresent());
        assertEquals(id1.get(), id2.get());

        assertEquals("test", id1.get());
    }

    @Test
    void testCustomLockMode() {
        EntityManager spyEntityManager = mock(EntityManager.class);

        JpaTokenStore testSubject = JpaTokenStore.builder()
                                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                                 .loadingLockMode(LockModeType.NONE)
                                                 .entityManagerProvider(new SimpleEntityManagerProvider(spyEntityManager))
                                                 .nodeId("test")
                                                 .build();

        try {
            testSubject.fetchToken("processorName", 1);
        } catch (Exception e) {
            // ignore. This fails
        }
        verify(spyEntityManager).find(eq(TokenEntry.class), any(), eq(LockModeType.NONE));
    }

    @Test
    void testInitializeTokens() {
        jpaTokenStore.initializeTokenSegments("test1", 7);

        int[] actual = jpaTokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);
    }

    @SuppressWarnings("Duplicates")
    @Test
    void testInitializeTokensAtGivenPosition() {
        jpaTokenStore.initializeTokenSegments("test1", 7, new GlobalSequenceTrackingToken(10));

        int[] actual = jpaTokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);

        for (int segment : actual) {
            assertEquals(new GlobalSequenceTrackingToken(10), jpaTokenStore.fetchToken("test1", segment));
        }
    }

    @Test
    void testInitializeTokensWhileAlreadyPresent() {
        assertThrows(UnableToClaimTokenException.class, () -> jpaTokenStore.fetchToken("test1", 1));
    }

    @Test
    void testDeleteTokenRejectedIfNotClaimedOrNotInitialized() {
        jpaTokenStore.initializeTokenSegments("test", 2);

        try {
            jpaTokenStore.deleteToken("test", 0);
            fail("Expected delete to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }

        try {
            jpaTokenStore.deleteToken("unknown", 0);
            fail("Expected delete to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void testDeleteToken() {
        jpaTokenStore.initializeSegment(null, "delete", 0);
        jpaTokenStore.fetchToken("delete", 0);

        entityManager.flush();
        jpaTokenStore.deleteToken("delete", 0);

        assertEquals(0L, (long) entityManager.createQuery("SELECT count(t) FROM TokenEntry t " +
                                                                  "WHERE t.processorName = :processorName", Long.class)
                                             .setParameter("processorName", "delete")
                                             .getSingleResult());
    }

    @Test
    void testClaimAndUpdateToken() {
        jpaTokenStore.initializeTokenSegments("test", 1);

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

        entityManager.flush();
        entityManager.clear();

        TokenEntry token = entityManager.find(TokenEntry.class, new TokenEntry.PK("test", 0));
        assertNull(token.getOwner());
    }

    @Test
    void testFetchTokenBySegment() {
        jpaTokenStore.initializeTokenSegments("test", 2);
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertNull(jpaTokenStore.fetchToken("test", segmentToFetch));
    }

    @Test
    void testFetchTokenBySegmentSegment0() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertNull(jpaTokenStore.fetchToken("test", segmentToFetch));
    }

    @Test
    void testFetchTokenBySegmentFailsDuringMerge() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        //Create a segment as if there would be two segments in total. This simulates that these two segments have been merged into one.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> jpaTokenStore.fetchToken("test", segmentToFetch)
        );
    }

    @Test
    void testFetchTokenBySegmentFailsDuringMergeSegment0() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        Segment segmentToFetch = Segment.computeSegment(0, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> jpaTokenStore.fetchToken("test", segmentToFetch)
        );
    }

    @Test
    void testFetchTokenBySegmentFailsDuringSplit() {
        jpaTokenStore.initializeTokenSegments("test", 4);
        //Create a segment as if there would be only two segments in total. This simulates that the segments have been split into 4 segments.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThrows(UnableToClaimTokenException.class,
                     () -> jpaTokenStore.fetchToken("test", segmentToFetch)
        );
    }

    @Test
    void testFetchTokenBySegmentFailsDuringSplitSegment0() {
        jpaTokenStore.initializeTokenSegments("test", 2);
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertThrows(UnableToClaimTokenException.class,
                     () -> jpaTokenStore.fetchToken("test", segmentToFetch)
        );
    }

    @Test
    void testQuerySegments() {
        prepareTokenStore();

        {
            final int[] segments = jpaTokenStore.fetchSegments("proc1");
            assertThat(segments.length, is(2));
        }
        {
            final int[] segments = jpaTokenStore.fetchSegments("proc2");
            assertThat(segments.length, is(1));
        }
        {
            final int[] segments = jpaTokenStore.fetchSegments("proc3");
            assertThat(segments.length, is(0));
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void testQueryAvailableSegments() {
        prepareTokenStore();

        {
            final List<Segment> segments = concurrentJpaTokenStore.fetchAvailableSegments("proc1");
            assertThat(segments.size(), is(0));
            jpaTokenStore.releaseClaim("proc1", 0);
            entityManager.flush();
            entityManager.clear();
            final List<Segment> segmentsAfterRelease = concurrentJpaTokenStore.fetchAvailableSegments("proc1");
            assertThat(segmentsAfterRelease.size(), is(1));
        }
        {
            final List<Segment> segments = concurrentJpaTokenStore.fetchAvailableSegments("proc2");
            assertThat(segments.size(), is(0));
            jpaTokenStore.releaseClaim("proc2", 0);
            entityManager.flush();
            entityManager.clear();
            final List<Segment> segmentsAfterRelease = concurrentJpaTokenStore.fetchAvailableSegments("proc2");
            assertThat(segmentsAfterRelease.size(), is(1));
        }
        {
            final List<Segment> segments = jpaTokenStore.fetchAvailableSegments("proc3");
            assertThat(segments.size(), is(0));
        }

        entityManager.flush();
        entityManager.clear();
    }

    private void prepareTokenStore() {
        jpaTokenStore.initializeTokenSegments("test", 1);
        jpaTokenStore.initializeTokenSegments("proc1", 2);
        jpaTokenStore.initializeTokenSegments("proc2", 1);

        assertNull(jpaTokenStore.fetchToken("test", 0));

        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "proc1", 0);
        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc1", 1);
        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc2", 0);
    }

    @Test
    void testClaimTokenConcurrently() {
        jpaTokenStore.initializeTokenSegments("concurrent", 1);
        jpaTokenStore.fetchToken("concurrent", 0);
        try {
            concurrentJpaTokenStore.fetchToken("concurrent", 0);
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void testStealToken() {
        jpaTokenStore.initializeTokenSegments("stealing", 1);

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
    void testExtendingLostClaimFails() {
        jpaTokenStore.initializeTokenSegments("processor", 1);
        jpaTokenStore.fetchToken("processor", 0);

        try {
            stealingJpaTokenStore.extendClaim("processor", 0);
            fail("Expected claim extension to fail");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void testStoreAndLoadAcrossTransactions() {

        jpaTokenStore.initializeTokenSegments("multi", 1);
        newTransAction();

        jpaTokenStore.fetchToken("multi", 0);
        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(1), "multi", 0);
        newTransAction();

        TrackingToken actual = jpaTokenStore.fetchToken("multi", 0);
        assertEquals(new GlobalSequenceTrackingToken(1), actual);
        jpaTokenStore.storeToken(new GlobalSequenceTrackingToken(2), "multi", 0);
        newTransAction();

        actual = jpaTokenStore.fetchToken("multi", 0);
        assertEquals(new GlobalSequenceTrackingToken(2), actual);
    }

    private JpaTokenStore getTokenStore(String nodeId, @Nullable TemporalAmount claimTimeOut){
        JpaTokenStore.Builder builder = JpaTokenStore.builder()
                                             .entityManagerProvider(entityManagerProvider)
                                             .serializer(TestSerializer.XSTREAM.getSerializer())
                                             .nodeId(nodeId);
        if (! Objects.isNull(claimTimeOut)){
            builder.claimTimeout(claimTimeOut);
        }
        return builder.build();
    }

    private void newTransAction(){
        entityManager.flush();
        entityManager.clear();
        transaction.commit();
        transaction = entityManager.getTransaction();
        transaction.begin();
    }
}
