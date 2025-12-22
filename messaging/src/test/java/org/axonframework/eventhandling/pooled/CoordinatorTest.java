/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventhandling.pooled;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.stubbing.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.eventhandling.Segment.computeSegment;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Coordinator}.
 *
 * @author Fabio Couto
 */
class CoordinatorTest {

    private static final String PROCESSOR_NAME = "test";

    private Coordinator testSubject;

    private final Segment SEGMENT_ZERO = computeSegment(0);
    private final int SEGMENT_ID = 0;
    private final int[] SEGMENT_IDS = {SEGMENT_ID};
    private final Segment SEGMENT_ONE = Segment.computeSegment(SEGMENT_ID, SEGMENT_IDS);
    private final int[] EMPTY_SEGMENT_IDS = {};

    private final TokenStore tokenStore = mock(TokenStore.class);
    private final ScheduledThreadPoolExecutor executorService = mock(ScheduledThreadPoolExecutor.class);
    @SuppressWarnings("unchecked")
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource = mock(StreamableMessageSource.class);

    private final WorkPackage workPackage = mock(WorkPackage.class);

    @BeforeEach
    void setUp() {
        testSubject = Coordinator.builder()
                                 .name(PROCESSOR_NAME)
                                 .messageSource(messageSource)
                                 .tokenStore(tokenStore)
                                 .transactionManager(NoTransactionManager.instance())
                                 .executorService(executorService)
                                 .workPackageFactory((segment, trackingToken) -> workPackage)
                                 .initialToken(es -> ReplayToken.createReplayToken(es.createHeadToken()))
                                 .eventFilter(eventMessage -> true)
                                 .maxSegmentProvider(e -> SEGMENT_IDS.length)
                                 .build();
    }

    @Test
    void ifCoordinationTaskRescheduledAfterTokenReleaseClaimFails() {
        //arrange
        final RuntimeException streamOpenException = new RuntimeException("Some exception during event stream open");
        final RuntimeException releaseClaimException = new RuntimeException("Some exception during release claim");
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        doReturn(token).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt());
        doThrow(releaseClaimException).when(tokenStore).releaseClaim(eq(PROCESSOR_NAME), anyInt());
        //noinspection resource
        doThrow(streamOpenException).when(messageSource).openStream(any());
        doReturn(completedFuture(streamOpenException)).when(workPackage).abort(any());
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        //act
        testSubject.start();

        //asserts
        verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        // should be zero since we mock there already is a segment
        verify(tokenStore, times(0)).initializeTokenSegments(anyString(), anyInt(), any(TrackingToken.class));
    }

    @Test
    void ifCoordinationTaskInitializesTokenStoreWhenNeeded() {
        //arrange
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(EMPTY_SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        doReturn(token).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt());
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        //act
        testSubject.start();

        //asserts
        verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(tokenStore, times(1)).initializeTokenSegments(anyString(), anyInt(), isNull());
    }

    @SuppressWarnings("rawtypes") // Mockito cannot deal with the wildcard generics of the TrackedEventMessage
    @Test
    void ifCoordinationTaskSchedulesEventsWithTheSameTokenTogether() throws InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(0);
        TrackedEventMessage testEventOne =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage testEventTwo =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        when(workPackage.hasRemainingCapacity()).thenReturn(true)
                                                .thenReturn(false);
        when(workPackage.isAbortTriggered()).thenReturn(false);
        when(workPackage.scheduleEvents(testEvents)).thenReturn(true);

        //noinspection unchecked
        BlockingStream<TrackedEventMessage<?>> testStream = mock(BlockingStream.class);
        when(testStream.setOnAvailableCallback(any())).thenReturn(false);
        when(testStream.hasNextAvailable()).thenReturn(true)
                                           .thenReturn(true)
                                           .thenReturn(false);
        //noinspection unchecked
        when(testStream.nextAvailable()).thenReturn(testEventOne)
                                        .thenReturn(testEventTwo);
        //noinspection unchecked
        when(testStream.peek()).thenReturn(Optional.of(testEventTwo))
                               .thenReturn(Optional.of(testEventTwo))
                               .thenReturn(Optional.empty());

        when(executorService.submit(any(Runnable.class))).thenAnswer(runTaskAsync());
        when(tokenStore.fetchSegments(PROCESSOR_NAME)).thenReturn(SEGMENT_IDS);
        when(tokenStore.fetchAvailableSegments(PROCESSOR_NAME)).thenReturn(Collections.singletonList(SEGMENT_ONE));
        when(tokenStore.fetchToken(PROCESSOR_NAME, SEGMENT_ONE)).thenReturn(testToken);
        when(messageSource.openStream(testToken)).thenReturn(testStream);

        testSubject.start();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> verify(tokenStore).fetchToken(PROCESSOR_NAME, SEGMENT_ONE));
        //noinspection resource
        assertWithin(500, TimeUnit.MILLISECONDS, () -> verify(messageSource).openStream(testToken));

        //noinspection unchecked
        ArgumentCaptor<List<TrackedEventMessage<?>>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        assertWithin(500, TimeUnit.MILLISECONDS, () -> verify(workPackage).scheduleEvents(eventsCaptor.capture()));

        List<TrackedEventMessage<?>> resultEvents = eventsCaptor.getValue();
        assertEquals(2, resultEvents.size());
        assertTrue(resultEvents.contains(testEventOne));
        assertTrue(resultEvents.contains(testEventTwo));

        verify(workPackage, times(0)).scheduleEvent(any());
    }

    @Test
    void coordinatorShouldNotTryToOpenStreamWithNoToken() throws NoSuchFieldException {
        //arrange
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        doReturn(token).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt());
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));
        //Using reflection to add a work package to keep the test simple
        Map<Integer, WorkPackage> workPackages =
                ReflectionUtils.getFieldValue(Coordinator.class.getDeclaredField("workPackages"), testSubject);
        workPackages.put(SEGMENT_ID, workPackage);
        CompletableFuture<Exception> abortFuture = new CompletableFuture<>();
        doReturn(abortFuture).when(workPackage).abort(any());

        //act
        testSubject.start();

        //asserts
        verify(messageSource, never()).openStream(any(TrackingToken.class));
    }

    /**
     * This test demonstrates that a NullPointerException occurs when:
     * 1. Work packages exist in the coordinator (from a previous run or incomplete abort)
     * 2. No new segments are claimed (claimNewSegments returns empty)
     * 3. The event stream is null (never opened or closed during abort)
     * 4. The coordinator tries to access eventStream.hasNextAvailable()
     *
     * This scenario can occur during error recovery when the abort process doesn't
     * fully clear the workPackages map before the new CoordinationTask runs.
     *
     * @see <a href="https://github.com/AxonFramework/AxonFramework/issues/XXXX">GitHub Issue</a>
     */
    @Test
    void coordinatorShouldNotThrowNpeWhenWorkPackagesExistButStreamIsNull() throws NoSuchFieldException {
        // Arrange: Set up a coordinator with existing work packages but no stream
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        // Return empty list - no new segments to claim (they're already in workPackages)
        doReturn(Collections.emptyList()).when(tokenStore).fetchAvailableSegments(PROCESSOR_NAME);
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doReturn(false).when(workPackage).isAbortTriggered();
        doReturn(true).when(workPackage).hasRemainingCapacity();
        doReturn(completedFuture(null)).when(workPackage).abort(any());
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        // Using reflection to simulate the state after an incomplete abort:
        // - workPackages map has entries (not cleared)
        // - but the new CoordinationTask has eventStream = null
        Map<Integer, WorkPackage> workPackages =
                ReflectionUtils.getFieldValue(Coordinator.class.getDeclaredField("workPackages"), testSubject);
        workPackages.put(SEGMENT_ID, workPackage);

        // Act & Assert: Should NOT throw NullPointerException
        // Currently this WILL throw NPE at: eventStream.hasNextAvailable()
        assertDoesNotThrow(() -> testSubject.start());
    }

    /**
     * This test demonstrates the race condition during abort recovery:
     * 1. First coordination fails (e.g., stream opening throws exception)
     * 2. abortAndScheduleRetry is called
     * 3. New CoordinationTask is created with fresh eventStream=null
     * 4. But workPackages might not be cleared yet (async)
     * 5. NPE when accessing eventStream
     */
    @Test
    void coordinatorShouldHandleRaceConditionDuringAbortRecovery() throws NoSuchFieldException {
        // Arrange
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);
        final RuntimeException streamException = new RuntimeException("Simulated stream failure");

        doReturn(SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        doReturn(Collections.singletonList(SEGMENT_ONE)).when(tokenStore).fetchAvailableSegments(PROCESSOR_NAME);
        doReturn(token).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), any(Segment.class));
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doReturn(false).when(workPackage).isAbortTriggered();
        doReturn(true).when(workPackage).hasRemainingCapacity();

        // Make abort() return a future that completes but doesn't clear workPackages fast enough
        CompletableFuture<Void> slowAbortFuture = new CompletableFuture<>();
        doReturn(slowAbortFuture).when(workPackage).abort(any());

        // First call opens stream successfully, second call (during retry) returns null stream scenario
        @SuppressWarnings("unchecked")
        BlockingStream<TrackedEventMessage<?>> testStream = mock(BlockingStream.class);
        when(testStream.setOnAvailableCallback(any())).thenReturn(false);
        // First hasNextAvailable throws to trigger abort
        when(testStream.hasNextAvailable()).thenThrow(streamException);

        when(messageSource.openStream(any())).thenReturn(testStream);

        // Capture the scheduled retry task
        ArgumentCaptor<Runnable> retryTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));
        doAnswer(invocation -> {
            // Don't run the retry task immediately - we want to inspect the state
            return mock(Future.class);
        }).when(executorService).schedule(retryTaskCaptor.capture(), anyLong(), any(TimeUnit.class));

        // Act: Start the coordinator - this will fail and schedule a retry
        testSubject.start();

        // Verify abort was triggered
        verify(workPackage).abort(streamException);

        // Now complete the abort future (simulating async completion)
        slowAbortFuture.complete(null);

        // Simulate the workPackages not being cleared (race condition)
        Map<Integer, WorkPackage> workPackagesMap =
                ReflectionUtils.getFieldValue(Coordinator.class.getDeclaredField("workPackages"), testSubject);
        workPackagesMap.put(SEGMENT_ID, workPackage);

        // Make fetchAvailableSegments return empty (segments are "claimed" in workPackages)
        doReturn(Collections.emptyList()).when(tokenStore).fetchAvailableSegments(PROCESSOR_NAME);

        // Act: Run the retry task - this should NOT throw NPE
        // Currently this WILL throw NPE because eventStream is null but workPackages is not empty
        Runnable retryTask = retryTaskCaptor.getValue();
        assertDoesNotThrow(() -> retryTask.run(),
                "Coordinator should handle the case where workPackages exist but eventStream is null");
    }

    /**
     * This test verifies the coordinator gracefully handles the scenario where
     * the event stream cannot be opened because the tracking token is a NoToken,
     * but work packages already exist.
     */
    @Test
    void coordinatorShouldNotThrowNpeWhenStreamCannotBeOpenedDueToNoToken() throws NoSuchFieldException {
        // Arrange
        doReturn(SEGMENT_IDS).when(tokenStore).fetchSegments(PROCESSOR_NAME);
        // No available segments to claim (all already in workPackages)
        doReturn(Collections.emptyList()).when(tokenStore).fetchAvailableSegments(PROCESSOR_NAME);
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doReturn(false).when(workPackage).isAbortTriggered();
        doReturn(true).when(workPackage).hasRemainingCapacity();
        doReturn(true).when(workPackage).isDone();
        doReturn(completedFuture(null)).when(workPackage).abort(any());

        // Pre-populate workPackages to simulate state after incomplete recovery
        Map<Integer, WorkPackage> workPackagesMap =
                ReflectionUtils.getFieldValue(Coordinator.class.getDeclaredField("workPackages"), testSubject);
        workPackagesMap.put(SEGMENT_ID, workPackage);

        // Track if we get past the problematic code
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        // Act & Assert
        // The coordinator should either:
        // 1. Detect the inconsistent state and trigger recovery, OR
        // 2. Skip coordination and reschedule
        // It should NOT throw NullPointerException
        assertDoesNotThrow(() -> testSubject.start(),
                "Coordinator should not throw NPE when workPackages exist but stream cannot be opened");

        // Verify that either the stream was opened OR a retry was scheduled
        // (not that an NPE occurred)
        verify(executorService, atLeastOnce()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    private Answer<Future<Void>> runTaskSync() {
        return invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return completedFuture(null);
        };
    }

    private Answer<Future<Void>> runTaskAsync() {
        return invocationOnMock -> CompletableFuture.runAsync(invocationOnMock.getArgument(0));
    }
}
