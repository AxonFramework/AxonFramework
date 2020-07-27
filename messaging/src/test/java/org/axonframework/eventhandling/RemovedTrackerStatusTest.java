/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling;

import org.junit.jupiter.api.*;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link RemovedTrackerStatus}.
 *
 * @author Steven van Beelen
 */
class RemovedTrackerStatusTest {

    @Test
    void testGetSegment() {
        Segment expectedSegment = Segment.ROOT_SEGMENT;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getSegment()).thenReturn(expectedSegment);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedSegment, testSubject.getSegment());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testIsCaughtUp() {
        boolean expectedIsCaughtUp = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isCaughtUp()).thenReturn(expectedIsCaughtUp);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedIsCaughtUp, testSubject.isCaughtUp());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testIsReplaying() {
        boolean expectedIsReplaying = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isReplaying()).thenReturn(expectedIsReplaying);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedIsReplaying, testSubject.isReplaying());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testIsMerging() {
        boolean expectedIsMerging = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isMerging()).thenReturn(expectedIsMerging);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedIsMerging, testSubject.isMerging());
    }

    @Test
    void testGetTrackingToken() {
        TrackingToken expectedTrackingToken = new GlobalSequenceTrackingToken(0);
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getTrackingToken()).thenReturn(expectedTrackingToken);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedTrackingToken, testSubject.getTrackingToken());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testIsErrorState() {
        boolean expectedIsErrorState = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isErrorState()).thenReturn(expectedIsErrorState);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedIsErrorState, testSubject.isErrorState());
    }

    @Test
    void testGetError() {
        Exception expectedException = new IllegalArgumentException("some-exception");
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getError()).thenReturn(expectedException);

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        assertEquals(expectedException, testSubject.getError());
    }

    @Test
    void testGetCurrentPosition() {
        long expectedCurrentPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getCurrentPosition()).thenReturn(OptionalLong.of(expectedCurrentPosition));

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        OptionalLong result = testSubject.getCurrentPosition();
        assertTrue(result.isPresent());
        assertEquals(expectedCurrentPosition, result.getAsLong());
    }

    @Test
    void testGetResetPosition() {
        long expectedResetPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getResetPosition()).thenReturn(OptionalLong.of(expectedResetPosition));

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        OptionalLong result = testSubject.getResetPosition();
        assertTrue(result.isPresent());
        assertEquals(expectedResetPosition, result.getAsLong());
    }

    @Test
    void testMergeCompletedPosition() {
        long expectedMergeCompletedPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.mergeCompletedPosition()).thenReturn(OptionalLong.of(expectedMergeCompletedPosition));

        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(delegate);

        OptionalLong result = testSubject.mergeCompletedPosition();
        assertTrue(result.isPresent());
        assertEquals(expectedMergeCompletedPosition, result.getAsLong());
    }

    @Test
    void testTrackerAdded() {
        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(mock(EventTrackerStatus.class));

        assertFalse(testSubject.trackerAdded());
    }

    @Test
    void testTrackerRemoved() {
        RemovedTrackerStatus testSubject = new RemovedTrackerStatus(mock(EventTrackerStatus.class));

        assertTrue(testSubject.trackerRemoved());
    }
}