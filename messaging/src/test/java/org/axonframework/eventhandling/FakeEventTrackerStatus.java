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

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Implementation of the {@link EventTrackerStatus} for testing purposes.
 *
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
public class FakeEventTrackerStatus implements EventTrackerStatus {

    private Segment segment;
    private TrackingToken trackingToken;
    private boolean caughtUp;
    private boolean replaying;
    private boolean merging;
    private boolean isError;
    private Throwable error;
    private Long currentPosition;
    private Long resetPosition;
    private Long mergeCompletedPosition;

    @Override
    public Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
    }

    @Override
    public TrackingToken getTrackingToken() {
        return trackingToken;
    }

    public void setTrackingToken(TrackingToken trackingToken) {
        this.trackingToken = trackingToken;
    }

    @Override
    public boolean isCaughtUp() {
        return caughtUp;
    }

    public void setCaughtUp(boolean caughtUp) {
        this.caughtUp = caughtUp;
    }

    @Override
    public boolean isReplaying() {
        return replaying;
    }

    public void setReplaying(boolean replaying) {
        this.replaying = replaying;
    }

    @Override
    public boolean isMerging() {
        return merging;
    }

    public void setMerging(boolean merging) {
        this.merging = merging;
    }

    @Override
    public boolean isErrorState() {
        return isError;
    }

    public void setErrorState(boolean error) {
        isError = error;
    }

    @Override
    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    @Override
    public OptionalLong getCurrentPosition() {
        return Objects.nonNull(currentPosition) ? OptionalLong.of(currentPosition) : OptionalLong.empty();
    }

    public void setCurrentPosition(Long currentPosition) {
        this.currentPosition = currentPosition;
    }

    @Override
    public OptionalLong getResetPosition() {
        return Objects.nonNull(resetPosition) ? OptionalLong.of(resetPosition) : OptionalLong.empty();
    }

    public void setResetPosition(Long resetPosition) {
        this.resetPosition = resetPosition;
    }

    @Override
    public OptionalLong mergeCompletedPosition() {
        return Objects.nonNull(mergeCompletedPosition) ? OptionalLong.of(mergeCompletedPosition) : OptionalLong.empty();
    }

    public void setMergeCompletedPosition(Long mergeCompletedPosition) {
        this.mergeCompletedPosition = mergeCompletedPosition;
    }
}
