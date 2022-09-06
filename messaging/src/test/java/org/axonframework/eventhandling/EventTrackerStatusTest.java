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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@code default} methods on the {@link EventTrackerStatus}.
 *
 * @author Steven van Beelen
 */
class EventTrackerStatusTest {

    private static final boolean DO_NOT_VALIDATE_POSITIONS = false;
    private static final boolean VALIDATE_POSITIONS = true;

    private FakeEventTrackerStatus thisStatus;
    private FakeEventTrackerStatus thatStatus;

    @BeforeEach
    void setUp() {
        thisStatus = new FakeEventTrackerStatus();
        thatStatus = new FakeEventTrackerStatus();
    }

    @Test
    void isDifferentReturnsFalseForIdenticalObjects() {
        assertFalse(thisStatus.isDifferent(thisStatus, DO_NOT_VALIDATE_POSITIONS));
        assertFalse(thisStatus.isDifferent(thisStatus));
    }

    @Test
    void isDifferentReturnsTrueForDifferentEventTrackerStatusImplementations() {
        AddedTrackerStatus otherStatus = new AddedTrackerStatus(thatStatus);
        assertTrue(thisStatus.isDifferent(otherStatus, DO_NOT_VALIDATE_POSITIONS));
        assertTrue(thisStatus.isDifferent(otherStatus));
    }

    @Test
    void isDifferentReturnsFalseForSimilarStatusObjects() {
        assertFalse(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS));
        assertFalse(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS));
    }

    @Test
    void isDifferentReturnsFalseForSimilarStatusObjectsWhenDisregardingPositions() {
        assertFalse(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS));
        assertFalse(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS));

        thisStatus.setMerging(true);
        thatStatus.setMerging(false);
        assertTrue(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS));
        assertTrue(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS));

        thisStatus.setMerging(false);
        thisStatus.setCurrentPosition(10L);
        thatStatus.setCurrentPosition(42L);
        assertFalse(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS));
        assertTrue(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS));
    }

    @Test
    void matchStates() {
        assertTrue(thisStatus.matchStates(thatStatus));

        thisStatus.setSegment(Segment.ROOT_SEGMENT);
        thisStatus.setTrackingToken(new GlobalSequenceTrackingToken(0L));
        thisStatus.setCaughtUp(true);
        thisStatus.setReplaying(true);
        thisStatus.setMerging(true);
        thisStatus.setErrorState(true);
        thisStatus.setError(new IllegalArgumentException("some-exception"));
        assertFalse(thisStatus.matchStates(thatStatus));

        thatStatus.setSegment(Segment.ROOT_SEGMENT);
        thatStatus.setTrackingToken(new GlobalSequenceTrackingToken(0L));
        thatStatus.setCaughtUp(true);
        thatStatus.setReplaying(true);
        thatStatus.setMerging(true);
        thatStatus.setErrorState(true);
        thatStatus.setError(new IllegalArgumentException("some-exception"));
        assertTrue(thisStatus.matchStates(thatStatus));
    }

    @Test
    void matchPositions() {
        assertTrue(thisStatus.matchPositions(thatStatus));

        thisStatus.setCurrentPosition(10L);
        thisStatus.setResetPosition(42L);
        thisStatus.setMergeCompletedPosition(1337L);
        assertFalse(thisStatus.matchPositions(thatStatus));

        thatStatus.setCurrentPosition(10L);
        thatStatus.setResetPosition(42L);
        thatStatus.setMergeCompletedPosition(1337L);
        assertTrue(thisStatus.matchPositions(thatStatus));
    }

    @Test
    void trackerAdded() {
        assertFalse(thisStatus.trackerAdded());
    }

    @Test
    void trackerRemoved() {
        assertFalse(thisStatus.trackerRemoved());
    }
}