package org.axonframework.eventhandling;

import java.util.Objects;
import java.util.OptionalLong;

public final class TrackerStatus implements EventTrackerStatus {

    private final Segment segment;
    private final boolean caughtUp;
    private final TrackingToken trackingToken;
    private final Throwable errorState;

    public TrackerStatus(Segment segment, TrackingToken trackingToken) {
        this(segment, false, trackingToken, null);
    }

    public TrackerStatus(Segment segment, boolean caughtUp, TrackingToken trackingToken, Throwable errorState) {
        this.segment = segment;
        this.caughtUp = caughtUp;
        this.trackingToken = trackingToken;
        this.errorState = errorState;
    }

    public TrackerStatus caughtUp() {
        if (caughtUp) {
            return this;
        }
        return new TrackerStatus(segment, true, trackingToken, null);
    }

    public TrackerStatus advancedTo(TrackingToken trackingToken) {
        if (Objects.equals(this.trackingToken, trackingToken)) {
            return this;
        }
        return new TrackerStatus(segment, caughtUp, trackingToken, null);
    }

    public TrackerStatus markError(Throwable error) {
        return new TrackerStatus(segment, caughtUp, trackingToken, error);
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

    /**
     * {@inheritDoc}
     */
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

    public TrackingToken getInternalTrackingToken() {
        return trackingToken;
    }

    /**
     * Splits the current status object to reflect the status of their underlying segments being split.
     *
     * @return an array with two status object, representing the status of the split segments.
     */
    public TrackerStatus[] split() {
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
            // we were in a replay. Need to re-initialize the replay wrapper
            splitTokens[0] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[0]);
            splitTokens[1] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[1]);
        }
        TrackerStatus[] newStatus = new TrackerStatus[2];
        newStatus[0] = new TrackerStatus(newSegments[0], splitTokens[0]);
        newStatus[1] = new TrackerStatus(newSegments[1], splitTokens[1]);
        return newStatus;
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
