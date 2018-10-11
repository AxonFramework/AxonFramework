/*
 * Copyright (c) 2010-2018. Axon Framework
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
     * The tracking token of the last event that has been seen by this Segment
     *
     * @return tracking token of the last event that has been seen by this Segment
     */
    TrackingToken getTrackingToken();
}
