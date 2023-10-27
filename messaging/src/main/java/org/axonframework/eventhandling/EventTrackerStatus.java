/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.eventhandling.tokenstore.TokenStore;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Interface describing the status of a {@link Segment} of a {@link TrackingEventProcessor}.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public interface EventTrackerStatus {

    /**
     * The segment for which this status is valid.
     *
     * @return segment for which this status is valid
     */
    Segment getSegment();

    /**
     * Whether the Segment of this status has caught up with the head of the event stream. Note that this is no
     * guarantee that this segment is still processing at (or near) real-time events. It merely indicates that this
     * segment has been at the head of the stream since it started processing. It may have fallen back since then.
     *
     * @return whether the Segment of this status has caught up with the head of the event stream
     */
    boolean isCaughtUp();

    /**
     * Indicates whether this Segment is still replaying previously processed Events.
     * <p>
     * Note that this method will only recognize a replay if the tokens have been reset using {@link
     * TrackingEventProcessor#resetTokens()}. Removing tokens directly from the underlying {@link TokenStore} will not
     * be recognized as a replay.
     *
     * @return {@code true} if this segment is replaying historic events after a {@link
     * TrackingEventProcessor#resetTokens() reset}, otherwise {@code false}
     */
    boolean isReplaying();

    /**
     * Indicates whether this Segment is still merging two (or more) Segments. The merging process will be done once all
     * Segments have reached the same position.
     *
     * @return {@code true} if this segment is merging Segments, otherwise {@code false}
     */
    boolean isMerging();

    /**
     * Return the estimated relative token position this Segment will have after a merge operation is complete. Will
     * return a non-empty result as long as {@link EventTrackerStatus#isMerging()} } returns true. In case no estimation
     * can be given or no merge in progress, an {@code OptionalLong.empty()} will be returned.
     *
     * @return return the estimated relative position this Segment will reach after a merge operation is complete.
     */
    OptionalLong mergeCompletedPosition();

    /**
     * The tracking token of the last event that has been seen by this Segment.
     * <p>
     * The returned tracking token represents the position of this segment in the event stream. In case of a recent
     * merge of segments, the token represents the lowest position of the two merged segments.
     *
     * @return tracking token of the last event that has been seen by this Segment
     */
    TrackingToken getTrackingToken();

    /**
     * Indicates whether this status represents an error. When this method return {@code true}, the {@link #getError()}
     * will return the exception that caused the failure.
     *
     * @return {@code true} if an error was reported, otherwise {@code false}
     */
    boolean isErrorState();

    /**
     * Returns the exception that caused processing to fail, if present. If the segment is being processed normally,
     * this method returns {@code null}.
     *
     * @return the exception that caused processing to fail, or {@code null} when processing normally
     */
    Throwable getError();

    /**
     * Return the estimated relative current token position this Segment represents. In case of replay is active, return
     * the estimated relative position reached by merge operation. In case of merge is active, return the estimated
     * relative position reached by merge operation. In case no estimation can be given, or no replay or merge in
     * progress, an {@code OptionalLong.empty()} will be returned.
     *
     * @return return the estimated relative current token position this Segment represents
     */
    OptionalLong getCurrentPosition();

    /**
     * Return the relative position at which a reset was triggered for this Segment. In case a replay finished or no
     * replay is active, an {@code OptionalLong.empty()} will be returned.
     *
     * @return the relative position at which a reset was triggered for this Segment
     */
    OptionalLong getResetPosition();

    /**
     * Returns a {@code boolean} describing whether this {@link EventTrackerStatus} is starting it's progress for the
     * first time. Particularly useful if the {@link EventTrackerStatusChangeListener} should react to added status'.
     *
     * @return {@code true} if this {@link EventTrackerStatus} just started, {@code false} otherwise
     */
    default boolean trackerAdded() {
        return false;
    }

    /**
     * Returns a {@code boolean} describing whether this {@link EventTrackerStatus} has just stopped it's progress.
     * Particularly useful if the {@link EventTrackerStatusChangeListener} should react to removed status'.
     *
     * @return {@code true} if this {@link EventTrackerStatus} was just removed, {@code false} otherwise
     */
    default boolean trackerRemoved() {
        return false;
    }

    /**
     * Check whether {@code this} {@link EventTrackerStatus} is different from {@code that}.
     *
     * @param that the other {@link EventTrackerStatus} to validate the difference with
     * @return {@code true} if both {@link EventTrackerStatus}'s are different, {@code false} otherwise
     */
    default boolean isDifferent(EventTrackerStatus that) {
        return isDifferent(that, true);
    }

    /**
     * Check whether {@code this} {@link EventTrackerStatus} is different from {@code that}.
     *
     * @param that              the other {@link EventTrackerStatus} to validate the difference with
     * @param validatePositions flag dictating whether {@link #matchPositions(EventTrackerStatus)} should be taken into
     *                          account when matching
     * @return {@code true} if both {@link EventTrackerStatus}'s are different, {@code false} otherwise
     */
    default boolean isDifferent(EventTrackerStatus that, boolean validatePositions) {
        if (this == that) {
            return false;
        }
        if (that == null || this.getClass() != that.getClass()) {
            return true;
        }
        boolean matchingStates = matchStates(that);
        boolean matchingPositions = !validatePositions || matchPositions(that);
        return !matchingStates || !matchingPositions;
    }

    /**
     * Match the boolean state fields of {@code this} and {@code that}. This means {@link #isCaughtUp()}, {@link
     * #isReplaying()}, {@link #isMerging()} and {@link #isErrorState()} are taken into account.
     *
     * @param that the other {@link EventTrackerStatus} to match with
     * @return {@code true} if the boolean fields of {@code this} and {@code that} match, otherwise {@code false}
     */
    default boolean matchStates(EventTrackerStatus that) {
        return Objects.equals(this.isCaughtUp(), that.isCaughtUp()) &&
                Objects.equals(this.isReplaying(), that.isReplaying()) &&
                Objects.equals(this.isMerging(), that.isMerging()) &&
                Objects.equals(this.isErrorState(), that.isErrorState());
    }

    /**
     * Match the position fields of {@code this} and {@code that}. This means {@link #getCurrentPosition()}, {@link
     * #getResetPosition()} and {@link #mergeCompletedPosition()} ()} are taken into account.
     *
     * @param that the other {@link EventTrackerStatus} to match with
     * @return {@code true} if the positions fields of {@code this} and {@code that} match, otherwise {@code false}
     */
    default boolean matchPositions(EventTrackerStatus that) {
        return Objects.equals(this.getCurrentPosition(), that.getCurrentPosition()) &&
                Objects.equals(this.getResetPosition(), that.getResetPosition()) &&
                Objects.equals(this.mergeCompletedPosition(), that.mergeCompletedPosition());
    }
}
