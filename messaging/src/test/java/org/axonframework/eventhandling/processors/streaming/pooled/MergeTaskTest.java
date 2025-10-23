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

package org.axonframework.eventhandling.processors.streaming.pooled;

import org.axonframework.common.FutureUtils;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.MergedTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToClaimTokenException;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link MergeTask}.
 *
 * @author Steven van Beelen
 */
class MergeTaskTest {

    private static final String PROCESSOR_NAME = "test";
    private static final int SEGMENT_TO_MERGE = 0;
    private static final int SEGMENT_TO_BE_MERGED = 1;
    private static final Segment SEGMENT_ZERO = Segment.splitBalanced(Segment.ROOT_SEGMENT, 1).get(SEGMENT_TO_MERGE);
    private static final Segment SEGMENT_ONE = Segment.splitBalanced(Segment.ROOT_SEGMENT, 1).get(SEGMENT_TO_BE_MERGED);
    private final Map<Integer, WorkPackage> workPackages = new HashMap<>();
    private final TokenStore tokenStore = mock(TokenStore.class);
    private final WorkPackage workPackageOne = mock(WorkPackage.class);
    private final WorkPackage workPackageTwo = mock(WorkPackage.class);
    private CompletableFuture<Boolean> result;
    private MergeTask testSubject;

    @BeforeEach
    void setUp() {
        result = new CompletableFuture<>();

        when(tokenStore.fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ZERO.getSegmentId()), any()))
            .thenReturn(completedFuture(SEGMENT_ZERO));
        when(tokenStore.fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ONE.getSegmentId()), any()))
            .thenReturn(completedFuture(SEGMENT_ONE));

        testSubject = new MergeTask(
                result, PROCESSOR_NAME, SEGMENT_TO_MERGE, workPackages, tokenStore,
                new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE)
        );
    }

    @Test
    void runReturnsFalseThroughSegmentIdsWhichCannotMerge() throws ExecutionException, InterruptedException {
        when(tokenStore.fetchSegment(eq(PROCESSOR_NAME), eq(Segment.ROOT_SEGMENT.getSegmentId()), any()))
            .thenReturn(completedFuture(Segment.ROOT_SEGMENT));

        testSubject.run();

        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(Segment.ROOT_SEGMENT.getSegmentId()), any());
        assertTrue(result.isDone());
        assertFalse(result.get());
    }

    @Test
    void runMergeSegmentsFromWorkPackages() throws ExecutionException, InterruptedException {
        TrackingToken testTokenToMerge = new GlobalSequenceTrackingToken(0);
        TrackingToken testTokenToBeMerged = new GlobalSequenceTrackingToken(1);

        when(workPackageOne.segment()).thenReturn(SEGMENT_ZERO);
        when(workPackageOne.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenReturn(completedFuture(testTokenToMerge));
        workPackages.put(SEGMENT_TO_MERGE, workPackageOne);
        when(workPackageTwo.segment()).thenReturn(SEGMENT_ONE);
        when(workPackageTwo.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.deleteToken(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.storeToken(any(), anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.releaseClaim(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any()))
                .thenReturn(completedFuture(testTokenToBeMerged));
        workPackages.put(SEGMENT_TO_BE_MERGED, workPackageTwo);

        ArgumentCaptor<TrackingToken> mergedTokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);

        testSubject.run();

        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ZERO.getSegmentId()), any());
        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ONE.getSegmentId()), any());
        verify(tokenStore).deleteToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any());
        verify(tokenStore).storeToken(mergedTokenCaptor.capture(),
                                      eq(PROCESSOR_NAME),
                                      eq(SEGMENT_TO_MERGE),
                                      any());
        TrackingToken resultToken = mergedTokenCaptor.getValue();
        assertTrue(resultToken.getClass().isAssignableFrom(MergedTrackingToken.class));
        assertEquals(testTokenToMerge, ((MergedTrackingToken) resultToken).lowerSegmentToken());
        assertEquals(testTokenToBeMerged, ((MergedTrackingToken) resultToken).upperSegmentToken());
        verify(tokenStore).releaseClaim(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any());

        assertTrue(result.isDone());
        assertTrue(result.get());
    }

    @Test
    void runMergeSegmentsAfterClaimingBoth() throws ExecutionException, InterruptedException {
        TrackingToken testTokenToMerge = new GlobalSequenceTrackingToken(0);
        TrackingToken testTokenToBeMerged = new GlobalSequenceTrackingToken(1);
        when(tokenStore.deleteToken(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.storeToken(any(), anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.releaseClaim(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenReturn(completedFuture(testTokenToMerge));
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any()))
                .thenReturn(completedFuture(testTokenToBeMerged));

        ArgumentCaptor<TrackingToken> mergedTokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);

        testSubject.run();

        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ZERO.getSegmentId()), any());
        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ONE.getSegmentId()), any());
        verify(tokenStore).deleteToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any());
        verify(tokenStore).storeToken(mergedTokenCaptor.capture(),
                                      eq(PROCESSOR_NAME),
                                      eq(SEGMENT_TO_MERGE),
                                      any());
        TrackingToken resultToken = mergedTokenCaptor.getValue();
        assertTrue(resultToken.getClass().isAssignableFrom(MergedTrackingToken.class));
        assertEquals(testTokenToMerge, ((MergedTrackingToken) resultToken).lowerSegmentToken());
        assertEquals(testTokenToBeMerged, ((MergedTrackingToken) resultToken).upperSegmentToken());
        verify(tokenStore).releaseClaim(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any());

        assertTrue(result.isDone());
        assertTrue(result.get());
    }

    @Test
    void runMergeSegmentsFromWorkPackageAndClaimedSegment() throws ExecutionException, InterruptedException {
        TrackingToken testTokenToMerge = new GlobalSequenceTrackingToken(0);
        TrackingToken testTokenToBeMerged = new GlobalSequenceTrackingToken(1);

        when(workPackageOne.segment()).thenReturn(SEGMENT_ZERO);
        when(workPackageOne.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenReturn(completedFuture(testTokenToMerge));
        workPackages.put(SEGMENT_TO_MERGE, workPackageOne);
        when(tokenStore.deleteToken(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.storeToken(any(), anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.releaseClaim(anyString(), anyInt(), any())).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any()))
                .thenReturn(completedFuture(testTokenToBeMerged));

        ArgumentCaptor<TrackingToken> mergedTokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);

        testSubject.run();

        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ZERO.getSegmentId()), any());
        verify(tokenStore).fetchSegment(eq(PROCESSOR_NAME), eq(SEGMENT_ONE.getSegmentId()), any());
        verify(tokenStore).deleteToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any());
        verify(tokenStore).storeToken(mergedTokenCaptor.capture(),
                                      eq(PROCESSOR_NAME),
                                      eq(SEGMENT_TO_MERGE),
                                      any());
        TrackingToken resultToken = mergedTokenCaptor.getValue();
        assertTrue(resultToken.getClass().isAssignableFrom(MergedTrackingToken.class));
        assertEquals(testTokenToMerge, ((MergedTrackingToken) resultToken).lowerSegmentToken());
        assertEquals(testTokenToBeMerged, ((MergedTrackingToken) resultToken).upperSegmentToken());
        verify(tokenStore).releaseClaim(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any());

        assertTrue(result.isDone());
        assertTrue(result.get());
    }

    @Test
    void runCompletesExceptionallyThroughUnableToClaimTokenExceptionOnFetch() {
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenThrow(new UnableToClaimTokenException("some exception"));

        testSubject.run();

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    void runCompletesExceptionallyThroughUnableToClaimTokenExceptionOnDelete() {
        when(workPackageOne.segment()).thenReturn(SEGMENT_ZERO);
        when(workPackageOne.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenReturn(completedFuture(new GlobalSequenceTrackingToken(0)));
        workPackages.put(SEGMENT_TO_MERGE, workPackageOne);
        when(workPackageTwo.segment()).thenReturn(SEGMENT_ONE);
        when(workPackageTwo.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any()))
                .thenReturn(completedFuture(new GlobalSequenceTrackingToken(1)));
        workPackages.put(SEGMENT_TO_BE_MERGED, workPackageTwo);

        doThrow(new UnableToClaimTokenException("some exception"))
                .when(tokenStore)
                .deleteToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any());

        testSubject.run();

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    void runCompletesExceptionallyThroughOtherException() {
        when(workPackageOne.segment()).thenReturn(SEGMENT_ZERO);
        when(workPackageOne.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_MERGE), any()))
                .thenReturn(completedFuture(new GlobalSequenceTrackingToken(0)));
        workPackages.put(SEGMENT_TO_MERGE, workPackageOne);
        when(workPackageTwo.segment()).thenReturn(SEGMENT_ONE);
        when(workPackageTwo.abort(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any()))
                .thenReturn(completedFuture(new GlobalSequenceTrackingToken(1)));
        workPackages.put(SEGMENT_TO_BE_MERGED, workPackageTwo);

        doThrow(new IllegalStateException("some exception"))
                .when(tokenStore)
                .deleteToken(eq(PROCESSOR_NAME), eq(SEGMENT_TO_BE_MERGED), any());

        testSubject.run();

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void description() {
        String result = testSubject.getDescription();
        assertNotNull(result);
        assertTrue(result.contains("Merge"));
    }
}