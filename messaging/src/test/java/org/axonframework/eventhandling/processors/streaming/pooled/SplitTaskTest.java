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

import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.segmenting.TrackerStatus;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToClaimTokenException;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.common.FutureUtils.emptyCompletedFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SplitTask}.
 *
 * @author Steven van Beelen
 */
class SplitTaskTest {

    private static final String PROCESSOR_NAME = "test";
    private static final int SEGMENT_ID = Segment.ROOT_SEGMENT.getSegmentId();
    private final Map<Integer, WorkPackage> workPackages = new HashMap<>();
    private final TokenStore tokenStore = mock(TokenStore.class);
    private final WorkPackage workPackage = mock(WorkPackage.class);
    private CompletableFuture<Boolean> result;
    private SplitTask testSubject;

    @BeforeEach
    void setUp() {
        result = new CompletableFuture<>();

        testSubject = new SplitTask(
                result, PROCESSOR_NAME, SEGMENT_ID, workPackages, tokenStore, UnitOfWorkTestUtils.SIMPLE_FACTORY
        );
    }

    @Test
    void runSplitsSegmentFromWorkPackage() throws ExecutionException, InterruptedException {
        Segment testSegmentToSplit = Segment.ROOT_SEGMENT;
        TrackingToken testTokenToSplit = new GlobalSequenceTrackingToken(0);

        TrackerStatus[] expectedTokens = TrackerStatus.split(testSegmentToSplit, testTokenToSplit);
        TrackerStatus expectedOriginal = expectedTokens[0];
        TrackerStatus expectedSplit = expectedTokens[1];

        when(workPackage.segment()).thenReturn(testSegmentToSplit);
        when(workPackage.abort(null)).thenReturn(emptyCompletedFuture());
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_ID), any()))
                .thenReturn(completedFuture(testTokenToSplit));
        when(tokenStore.initializeSegment(any(), anyString(), anyInt(), any())).thenReturn(emptyCompletedFuture());
        when(tokenStore.releaseClaim(any(), anyInt(), any())).thenReturn(emptyCompletedFuture());
        workPackages.put(SEGMENT_ID, workPackage);

        testSubject.run();

        verify(tokenStore).initializeSegment(eq(expectedSplit.getTrackingToken()),
                                             eq(PROCESSOR_NAME),
                                             eq(expectedSplit.getSegment().getSegmentId()),
                                             any());
        verify(tokenStore).releaseClaim(eq(PROCESSOR_NAME),
                                        eq(expectedOriginal.getSegment().getSegmentId()),
                                        any());
        assertTrue(result.isDone());
        assertTrue(result.get());
    }

    @Test
    void runSplitsSegmentAfterClaiming() throws ExecutionException, InterruptedException {
        int[] testSegmentIds = {SEGMENT_ID};
        Segment testSegmentToSplit = Segment.computeSegment(SEGMENT_ID, testSegmentIds);
        TrackingToken testTokenToSplit = new GlobalSequenceTrackingToken(0);

        TrackerStatus[] expectedTokens = TrackerStatus.split(testSegmentToSplit, testTokenToSplit);
        TrackerStatus expectedOriginal = expectedTokens[0];
        TrackerStatus expectedSplit = expectedTokens[1];

        when(tokenStore.fetchSegments(eq(PROCESSOR_NAME), any()))
                .thenReturn(completedFuture(testSegmentIds));
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_ID), any()))
                .thenReturn(completedFuture(testTokenToSplit));
        when(tokenStore.initializeSegment(any(), anyString(), anyInt(), any())).thenReturn(emptyCompletedFuture());
        when(tokenStore.releaseClaim(any(), anyInt(), any())).thenReturn(emptyCompletedFuture());

        testSubject.run();

        verify(tokenStore).initializeSegment(eq(expectedSplit.getTrackingToken()),
                                             eq(PROCESSOR_NAME),
                                             eq(expectedSplit.getSegment().getSegmentId()),
                                             any());
        verify(tokenStore).releaseClaim(eq(PROCESSOR_NAME),
                                        eq(expectedOriginal.getSegment().getSegmentId()),
                                        any());
        assertTrue(result.isDone());
        assertTrue(result.get());
    }

    @Test
    void runCompletesExceptionallyThroughUnableToClaimTokenException() {
        when(tokenStore.fetchSegments(eq(PROCESSOR_NAME), any()))
                .thenReturn(completedFuture(new int[]{SEGMENT_ID}));
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_ID), any()))
                .thenThrow(new UnableToClaimTokenException("some exception"));

        testSubject.run();

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertThrows(ExecutionException.class, () -> result.get());
    }

    @Test
    void runCompletesExceptionallyThroughOtherException() {
        when(tokenStore.fetchSegments(eq(PROCESSOR_NAME), any()))
                .thenThrow(new IllegalStateException("some exception"));

        testSubject.run();

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertThrows(ExecutionException.class, () -> result.get());
    }

    @Test
    void description() {
        String result = testSubject.getDescription();
        assertNotNull(result);
        assertTrue(result.contains("Split"));
    }
}