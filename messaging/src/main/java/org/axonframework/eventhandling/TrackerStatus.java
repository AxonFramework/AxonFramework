/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
 * Implementation of the {@link EventTrackerStatus}, providing simply modification methods to switch from one {@code
 * EventTrackerStatus} value object to another.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public final class TrackerStatus implements EventTrackerStatus {

    private final Segment segment;
    private final boolean caughtUp;
    private final TrackingToken trackingToken;
    private final Throwable errorState;

    /**
     * Construct a {@link EventTrackerStatus} to portray the status of the given {@code segment} and {@code
     * trackingToken}.
     *
     * @param segment       the {@link Segment} this {@link EventTrackerStatus} shares the status of
     * @param trackingToken the {@link TrackingToken} this {@link EventTrackerStatus} shares the status of
     */
    public TrackerStatus(Segment segment, TrackingToken trackingToken) {
        this(segment, false, trackingToken, null);
    }

    /**
     * Construct a {@link EventTrackerStatus} to portray the status of the given {@code segment} and {@code
     * trackingToken}. The {@code caughtUp boolean} specifies whether the {@code trackingToken} reached the head of the
     * stream at least once. A {@link Throwable} can be provided to signal this {@code EventTrackerStatus} is in an
     * error state
     *
     * @param segment       the {@link Segment} this {@link EventTrackerStatus} shares the status of
     * @param caughtUp      a {@code boolean} specifying whether this {@link EventTrackerStatus} reached the head of the
     *                      stream at least once.
     * @param trackingToken the {@link TrackingToken} this {@link EventTrackerStatus} shares the status of
     * @param errorState    a {@link Throwable} defining the error status of this {@link EventTrackerStatus}. If {@code
     *                      null}, the status is not in an error state
     */
    public TrackerStatus(Segment segment, boolean caughtUp, TrackingToken trackingToken, Throwable errorState) {
        this.segment = segment;
        this.caughtUp = caughtUp;
        this.trackingToken = trackingToken;
        this.errorState = errorState;
    }

    /**
     * Returns this {@link TrackerStatus} if it is caught up, otherwise return a new instance with the caught up flag
     * set to true.
     *
     * @return this {@link TrackerStatus} if it is caught up, otherwise return a new instance with the caught up flag
     * set to true
     */
    public TrackerStatus caughtUp() {
        return caughtUp ? this : new TrackerStatus(segment, true, trackingToken, null);
    }

    /**
     * Advance this {@link TrackerStatus}' {@link TrackingToken} towards the given {@code trackingToken}. If this
     * status' token is identical to the given token, return {@code this}. Otherwise create a new {@code TrackerStatus}
     * instance using the given {@code trackingToken}.
     *
     * @param trackingToken the {@link TrackingToken} to advance this {@link TrackerStatus}' token towards, if they are
     *                      not the same
     * @return this {@link TrackerStatus} if the given {@code trackingToken} is identical to the current one, otherwise
     * a new {@code TrackerStatus} instance with the given {@code trackingToken}
     */
    public TrackerStatus advancedTo(TrackingToken trackingToken) {
        return Objects.equals(this.trackingToken, trackingToken)
                ? this : new TrackerStatus(segment, caughtUp, trackingToken, null);
    }

    /**
     * Return a new {@link TrackerStatus} based on this status, setting the given {@code error} as the {@code
     * errorState}.
     *
     * @param error the {@link Throwable} used to define the {@code errorState}
     * @return a new {@link TrackerStatus} based on this status, setting the given {@code error} as the {@code
     * errorState}
     */
    public TrackerStatus markError(Throwable error) {
        return new TrackerStatus(segment, caughtUp, trackingToken, error);
    }

    /**
     * Return a new {@link TrackerStatus} based on this status, removing the {@code errorState}.
     *
     * @return a new {@link TrackerStatus} based on this status, removing the {@code errorState}
     */
    public TrackerStatus unmarkError() {
        return new TrackerStatus(segment, caughtUp, trackingToken, null);
    }

    /**
     * Return the {@link TrackingToken} this {@link EventTrackerStatus} portrays the status of.
     *
     * @return the {@link TrackingToken} this {@link EventTrackerStatus} portrays the status of
     */
    public TrackingToken getInternalTrackingToken() {
        return trackingToken;
    }

    /**
     * Splits the current status object to reflect the status of their underlying segments being split.
     *
     * @return an array with two status objects, representing the status of the split segments
     */
    public TrackerStatus[] split() {
        return split(this.segment, this.trackingToken);
    }

    /**
     * Split the given {@code segment} and {@code trackingToken} in two. Constructs an array containing two {@link
     * TrackerStatus}s objects based on the split.
     *
     * @param segment       the {@link Segment} to split in two
     * @param trackingToken the {@link TrackingToken} to split in two
     * @return an array of two {@link TrackerStatus}s objects based on the split of the given {@code segment} and {@code
     * trackingToken}
     */
    public static TrackerStatus[] split(Segment segment, TrackingToken trackingToken) {
        Segment[] newSegments = segment.split();
        TrackingToken tokenAtReset = null;
        TrackingToken workingToken = trackingToken;
        TrackingToken[] splitTokens = new TrackingToken[2];

        if (workingToken instanceof ReplayToken) {
            tokenAtReset = ((ReplayToken) workingToken).getTokenAtReset();
            workingToken = ((ReplayToken) workingToken).lowerBound();
        }

        if (workingToken instanceof MergedTrackingToken) {
            splitTokens[0] = ((MergedTrackingToken) workingToken).lowerSegmentToken();
            splitTokens[1] = ((MergedTrackingToken) workingToken).upperSegmentToken();
        } else {
            splitTokens[0] = workingToken;
            splitTokens[1] = workingToken;
        }

        if (tokenAtReset != null) {
            // We were in a replay; need to re-initialize the replay wrapper.
            splitTokens[0] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[0]);
            splitTokens[1] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[1]);
        }

        TrackerStatus[] newStatus = new TrackerStatus[2];
        newStatus[0] = new TrackerStatus(newSegments[0], splitTokens[0]);
        newStatus[1] = new TrackerStatus(newSegments[1], splitTokens[1]);
        return newStatus;
    }

    @Override
    public Segment getSegment() {
        return segment;
    }

    @Override
    public boolean isCaughtUp() {
        return caughtUp;
    }

    @Override
    public boolean isReplaying() {
        return ReplayToken.isReplay(trackingToken);
    }

    @Override
    public boolean isMerging() {
        return MergedTrackingToken.isMergeInProgress(trackingToken);
    }

    @Override
    public OptionalLong mergeCompletedPosition() {
        return MergedTrackingToken.mergePosition(trackingToken);
    }

    @Override
    public TrackingToken getTrackingToken() {
        return WrappedToken.unwrapLowerBound(trackingToken);
    }

    @Override
    public boolean isErrorState() {
        return errorState != null;
    }

    @Override
    public Throwable getError() {
        return errorState;
    }

    @Override
    public OptionalLong getCurrentPosition() {
        if (isReplaying()) {
            return WrappedToken.unwrap(trackingToken, ReplayToken.class)
                               .map(ReplayToken::position)
                               .orElse(OptionalLong.empty());
        }

        if (isMerging()) {
            return WrappedToken.unwrap(trackingToken, MergedTrackingToken.class)
                               .map(MergedTrackingToken::position)
                               .orElse(OptionalLong.empty());
        }

        return (trackingToken == null) ? OptionalLong.empty() : trackingToken.position();
    }

    @Override
    public OptionalLong getResetPosition() {
        return ReplayToken.getTokenAtReset(trackingToken);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TrackerStatus that = (TrackerStatus) o;
        return caughtUp == that.caughtUp &&
                Objects.equals(segment, that.segment) &&
                Objects.equals(trackingToken, that.trackingToken) &&
                Objects.equals(errorState, that.errorState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, caughtUp, trackingToken, errorState);
    }

    @Override
    public String toString() {
        return "TrackerStatus{" +
                "segment=" + getSegment() +
                ", caughtUp=" + isCaughtUp() +
                ", replaying=" + isReplaying() +
                ", merging=" + isMerging() +
                ", errorState=" + isErrorState() +
                ", error=" + getError() +
                ", trackingToken=" + getTrackingToken() +
                ", currentPosition=" + getCurrentPosition() +
                ", resetPosition=" + getResetPosition() +
                ", mergeCompletedPosition=" + mergeCompletedPosition()
                + "}";
    }
}
