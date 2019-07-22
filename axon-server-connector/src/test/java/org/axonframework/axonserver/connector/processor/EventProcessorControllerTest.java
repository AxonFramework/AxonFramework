/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.processor;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EventProcessorControllerTest {

    private static final String TRACKING_PROCESSOR_NAME = "some-event-processor-name";
    private static final String SUBSCRIBING_PROCESSOR_NAME = "some-other-processor";
    private static final int SEGMENT_ID = 0;

    private final EventProcessingConfiguration eventProcessingConfiguration = mock(EventProcessingConfiguration.class);

    private final EventProcessorController testSubject = new EventProcessorController(eventProcessingConfiguration);

    private final TrackingEventProcessor testTrackingProcessor = mock(TrackingEventProcessor.class);
    private final SubscribingEventProcessor testSubscribingProcessor = mock(SubscribingEventProcessor.class);

    @Before
    public void setUp() {
        when(eventProcessingConfiguration.eventProcessor(TRACKING_PROCESSOR_NAME))
                .thenReturn(Optional.of(testTrackingProcessor));
        when(eventProcessingConfiguration.eventProcessor(SUBSCRIBING_PROCESSOR_NAME))
                .thenReturn(Optional.of(testSubscribingProcessor));

        when(testTrackingProcessor.splitSegment(SEGMENT_ID)).thenReturn(CompletableFuture.completedFuture(true));
        when(testTrackingProcessor.mergeSegment(SEGMENT_ID)).thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    public void testGetEventProcessorReturnsAnEventProcessor() {
        EventProcessor result = testSubject.getEventProcessor(TRACKING_PROCESSOR_NAME);

        assertEquals(testTrackingProcessor, result);
    }

    @Test(expected = RuntimeException.class)
    public void testGetEventProcessorThrowsRuntimeExceptionForNonExistingProcessor() {
        testSubject.getEventProcessor("non-existing-processor");
    }

    @Test
    public void testPauseProcessorCallsShutdownOnAnEventProcessor() {
        testSubject.pauseProcessor(TRACKING_PROCESSOR_NAME);

        verify(testTrackingProcessor).shutDown();
    }

    @Test
    public void testStartProcessorCallsStartOnAnEventProcessor() {
        testSubject.startProcessor(TRACKING_PROCESSOR_NAME);

        verify(testTrackingProcessor).start();
    }

    @Test
    public void testReleaseSegmentCallsReleaseSegmentOnAnEventProcessor() {
        testSubject.releaseSegment(TRACKING_PROCESSOR_NAME, SEGMENT_ID);

        verify(testTrackingProcessor).releaseSegment(SEGMENT_ID);
    }

    @Test
    public void testReleaseSegmentDoesNothingIfTheEventProcessorIsNotOfTypeTracking() {
        testSubject.releaseSegment(SUBSCRIBING_PROCESSOR_NAME, SEGMENT_ID);

        verifyZeroInteractions(testSubscribingProcessor);
    }

    @Test
    public void testSplitSegmentCallSplitOnAnEventProcessor() {
        boolean result = testSubject.splitSegment(TRACKING_PROCESSOR_NAME, SEGMENT_ID);

        verify(testTrackingProcessor).splitSegment(SEGMENT_ID);
        assertTrue(result);
    }

    @Test
    public void testSplitSegmentDoesNothingIfTheEventProcessorIsNotOfTypeTracking() {
        boolean result = testSubject.splitSegment(SUBSCRIBING_PROCESSOR_NAME, SEGMENT_ID);

        verifyZeroInteractions(testSubscribingProcessor);
        assertFalse(result);
    }

    @Test(expected = IllegalStateException.class)
    public void testSplitSegmentThrowsAnExceptionIfSplittingFails() {
        String testEventProcessorName = "failing-event-processor";
        TrackingEventProcessor testTrackingProcessor = mock(TrackingEventProcessor.class);

        when(eventProcessingConfiguration.eventProcessor(testEventProcessorName))
                .thenReturn(Optional.of(testTrackingProcessor));
        when(testTrackingProcessor.splitSegment(SEGMENT_ID)).thenThrow(new IllegalStateException("some-exception"));

        testSubject.splitSegment(testEventProcessorName, SEGMENT_ID);
    }

    @Test
    public void testMergeSegmentCallMergeOnAnEventProcessor() {
        boolean result = testSubject.mergeSegment(TRACKING_PROCESSOR_NAME, SEGMENT_ID);

        verify(testTrackingProcessor).mergeSegment(SEGMENT_ID);
        assertTrue(result);
    }

    @Test
    public void testMergeSegmentDoesNothingIfTheEventProcessorIsNotOfTypeTracking() {
        boolean result = testSubject.mergeSegment(SUBSCRIBING_PROCESSOR_NAME, SEGMENT_ID);

        verifyZeroInteractions(testSubscribingProcessor);
        assertFalse(result);
    }

    @Test(expected = IllegalStateException.class)
    public void testMergeSegmentThrowsAnExceptionIfMergingFails() {
        String testEventProcessorName = "failing-event-processor";
        TrackingEventProcessor testTrackingProcessor = mock(TrackingEventProcessor.class);

        when(eventProcessingConfiguration.eventProcessor(testEventProcessorName))
                .thenReturn(Optional.of(testTrackingProcessor));
        when(testTrackingProcessor.mergeSegment(SEGMENT_ID)).thenThrow(new IllegalStateException("some-exception"));

        testSubject.mergeSegment(testEventProcessorName, SEGMENT_ID);
    }
}