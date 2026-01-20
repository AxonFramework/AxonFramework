/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.SimpleEntry;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.stubbing.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.common.FutureUtils.emptyCompletedFuture;
import static org.axonframework.common.util.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;

/**
 * Test class validating the {@link Coordinator}.
 *
 * @author Fabio Couto
 */
class CoordinatorTest {

    private static final String PROCESSOR_NAME = "test";
    private final Segment SEGMENT_ZERO = Segment.ROOT_SEGMENT;
    private final int SEGMENT_ID = 0;
    private final List<Segment> SEGMENTS = List.of(Segment.ROOT_SEGMENT);
    private final Segment SEGMENT_ONE = new Segment(1, 1);
    private final TokenStore tokenStore = mock(TokenStore.class);
    private final ScheduledThreadPoolExecutor executorService = mock(ScheduledThreadPoolExecutor.class);
    @SuppressWarnings("unchecked")
    private final StreamableEventSource messageSource = mock(StreamableEventSource.class);
    private final WorkPackage workPackage = mock(WorkPackage.class);
    private Coordinator testSubject;

    private static Context trackingTokenContext(TrackingToken token) {
        return TrackingToken.addToContext(
                Context.empty(), token);
    }

    @Nonnull
    private static StreamingCondition streamingFrom(TrackingToken testToken) {
        return StreamingCondition.conditionFor(testToken, EventCriteria.havingAnyTag());
    }

    @BeforeEach
    void setUp() {
        when(messageSource.latestToken(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        when(messageSource.firstToken(null)).thenReturn(FutureUtils.emptyCompletedFuture());
        testSubject = Coordinator.builder()
                                 .name(PROCESSOR_NAME)
                                 .eventSource(messageSource)
                                 .tokenStore(tokenStore)
                                 .unitOfWorkFactory(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
                                 .executorService(executorService)
                                 .workPackageFactory((segment, trackingToken) -> workPackage)
                                 .initialToken(es -> es.firstToken(null).thenApply(ReplayToken::createReplayToken))
                                 .maxSegmentProvider(e -> SEGMENTS.size())
                                 .build();
    }

    @Test
    void ifCoordinationTaskRescheduledAfterTokenReleaseClaimFails() {
        //arrange
        final RuntimeException streamOpenException = new RuntimeException("Some exception during event stream open");
        final RuntimeException releaseClaimException = new RuntimeException("Some exception during release claim");
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(completedFuture(SEGMENTS)).when(tokenStore).fetchSegments(eq(PROCESSOR_NAME), any());
        doReturn(completedFuture(token)).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt(), any());
        doThrow(releaseClaimException).when(tokenStore).releaseClaim(eq(PROCESSOR_NAME), anyInt(), any());
        doThrow(streamOpenException).when(messageSource).open(any(), isNull());
        doReturn(completedFuture(streamOpenException)).when(workPackage).abort(any());
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        //act
        testSubject.start();

        //asserts
        verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        // should be zero since we mock there already is a segment
        verify(tokenStore, times(0)).initializeTokenSegments(anyString(),
                                                             anyInt(),
                                                             any(TrackingToken.class),
                                                             any(ProcessingContext.class));
    }

    @Test
    void ifCoordinationTaskInitializesTokenStoreWhenNeeded() {
        //arrange
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(completedFuture(List.of()))
                .when(tokenStore)
                .fetchSegments(eq(PROCESSOR_NAME), any());
        doReturn(emptyCompletedFuture())
                .when(tokenStore)
                .initializeTokenSegments(eq(PROCESSOR_NAME), anyInt(), any(), any(ProcessingContext.class)
                );
        doReturn(completedFuture(token)).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt(), any());
        doReturn(SEGMENT_ZERO).when(workPackage).segment();
        doAnswer(runTaskSync()).when(executorService).submit(any(Runnable.class));

        //act
        testSubject.start();

        //asserts
        verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(tokenStore, times(1)).initializeTokenSegments(anyString(),
                                                             anyInt(),
                                                             isNull(),
                                                             any(ProcessingContext.class));
    }

    @Test
    void ifCoordinationTaskSchedulesEventsWithTheSameTokenTogether() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(0);
        var testEventOne = EventTestUtils.asEventMessage("this-event");
        var testEventTwo = EventTestUtils.asEventMessage("this-event");
        var testEntryOne = new SimpleEntry<>(testEventOne, trackingTokenContext(testToken));
        var testEntryTwo = new SimpleEntry<>(testEventTwo, trackingTokenContext(testToken));
        List<MessageStream.Entry<? extends EventMessage>> testEntries = List.of(testEntryOne, testEntryTwo);

        when(workPackage.hasRemainingCapacity()).thenReturn(true)
                                                .thenReturn(false);
        when(workPackage.isAbortTriggered()).thenReturn(false);

        when(workPackage.scheduleEvents(testEntries)).thenReturn(true);
        when(workPackage.scheduleEvents(any())).thenReturn(true);

        MessageStream<EventMessage> testStream = MessageStream.fromIterable(
                List.of(testEventOne, testEventTwo),
                (e) -> trackingTokenContext(testToken)
        );

        when(executorService.submit(any(Runnable.class))).thenAnswer(runTaskAsync());
        when(tokenStore.fetchSegments(eq(PROCESSOR_NAME), any())).thenReturn(completedFuture(SEGMENTS));
        when(tokenStore.fetchAvailableSegments(eq(PROCESSOR_NAME), any()))
                .thenReturn(completedFuture(Collections.singletonList(SEGMENT_ONE)));
        when(tokenStore.fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_ONE), any()))
                .thenReturn(completedFuture(testToken));
        when(messageSource.open(streamingFrom(testToken), null)).thenReturn(testStream);

        testSubject.start();

        assertWithin(500, TimeUnit.MILLISECONDS,
                     () -> verify(tokenStore).fetchToken(eq(PROCESSOR_NAME), eq(SEGMENT_ONE), any()));

        assertWithin(500,
                     TimeUnit.MILLISECONDS,
                     () -> verify(messageSource).open(streamingFrom(testToken), null));

        //noinspection unchecked
        ArgumentCaptor<List<MessageStream.Entry<? extends EventMessage>>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        assertWithin(500, TimeUnit.MILLISECONDS, () -> verify(workPackage).scheduleEvents(eventsCaptor.capture()));

        var resultEvents = eventsCaptor.getValue();
        assertEquals(2, resultEvents.size());
        assertTrue(resultEvents.contains(testEntryOne));
        assertTrue(resultEvents.contains(testEntryTwo));

        verify(workPackage, times(0)).scheduleEvent(any());
    }

    @Test
    void coordinatorShouldNotTryToOpenStreamWithNoToken() throws NoSuchFieldException {
        //arrange
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(completedFuture(SEGMENTS)).when(tokenStore).fetchSegments(eq(PROCESSOR_NAME), any());
        doReturn(completedFuture(token)).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt(), any());
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
        verify(messageSource, never()).open(any(StreamingCondition.class), eq(null));
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