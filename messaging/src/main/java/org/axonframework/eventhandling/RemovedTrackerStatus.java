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

/**
 * References an {@link EventTrackerStatus} which no active work is occurring on. Can be used as a marker to react on in
 * the {@link EventTrackerStatusChangeListener}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class RemovedTrackerStatus extends WrappedTrackerStatus {

    /**
     * Initializes the {@link RemovedTrackerStatus} using the given {@code removedTrackerStatus}.
     *
     * @param removedTrackerStatus the removed {@link EventTrackerStatus} this implementation references
     */
    public RemovedTrackerStatus(EventTrackerStatus removedTrackerStatus) {
        super(removedTrackerStatus);
    }

    @Override
    public boolean trackerRemoved() {
        return true;
    }

    @Override
    public String toString() {
        return "RemovedTrackerStatus{" +
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
