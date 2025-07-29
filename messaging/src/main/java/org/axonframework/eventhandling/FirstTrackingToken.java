package org.axonframework.eventhandling;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A special {@link TrackingToken} that indicates the very beginning of an event stream.
 * <p>
 * Used to explicitly represent the first position in a stream instead of using {@code null}.
 */
public final class FirstTrackingToken implements TrackingToken {

    public static final FirstTrackingToken INSTANCE = new FirstTrackingToken();
    private FirstTrackingToken() {

    }

    /**
     * This token is always considered the lower bound since it represents the very beginning.
     */
    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        return this;
    }

    /**
     * The furthest known position is assumed to be the other token, since this token is the earliest possible one.
     */
    @Override
    public TrackingToken upperBound(TrackingToken other) {
        return other;
    }

    /**
     * This token does not cover any other tokens (except itself).
     */
    @Override
    public boolean covers(TrackingToken other) {
        return this.equals(other);
    }

    /**
     * The estimated position of this token is always 0.
     */
    @Override
    public OptionalLong position() {
        return OptionalLong.of(0L);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FirstTrackingToken;
    }

    @Override
    public int hashCode() {
        return Objects.hash("FirstTrackingToken");
    }

    @Override
    public String toString() {
        return "FirstTrackingToken";
    }
}
