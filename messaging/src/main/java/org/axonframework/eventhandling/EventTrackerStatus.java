/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.tokenstore.TokenStore;

import java.util.OptionalLong;

/**
 * Interface describing the status of a Segment of a TrackingProcessor.
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
     * Note that this method will only recognize a replay if the tokens have been reset using
     * {@link TrackingEventProcessor#resetTokens()}. Removing tokens directly from the underlying {@link TokenStore} will not be
     * recognized as a replay.
     *
     * @return {@code true} if this segment is replaying historic events after a {@link TrackingEventProcessor#resetTokens() reset}, otherwise {@code false}
     */
    boolean isReplaying();

    /**
     * Indicates whether this Segment is still merging two (or more) Segments. The merging process will be done once all Segments have reached the same position.
     *
     * @return {@code true} if this segment is merging Segments, otherwise {@code false}
     */
    boolean isMerging();

    /**
     * Return the estimated relative token position this Segment will have after a merge operation is complete.
     * Will return a non-empty result as long as {@link EventTrackerStatus#isMerging()} } returns true.
     * In case no estimation can be given or no merge in progress, an {@code OptionalLong.empty()} will be returned.
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
     * Return the estimated relative current token position this Segment represents.
     * In case of replay is active, return the estimated relative position reached by merge operation.
     * In case of merge is active, return the estimated relative position reached by merge operation.
     * In case no estimation can be given, or no replay or merge in progress, an {@code OptionalLong.empty()} will be returned.
     *
     * @return return the estimated relative current token position this Segment represents
     */
    OptionalLong getCurrentPosition();

    /**
     * Return the relative position at which a reset was triggered for this Segment.
     * In case a replay finished or no replay is active, an {@code OptionalLong.empty()} will be returned.
     *
     * @return the relative position at which a reset was triggered for this Segment
     */
    OptionalLong getResetPosition();
}
