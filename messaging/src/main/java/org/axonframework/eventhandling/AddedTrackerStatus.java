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

/**
 * References a new {@link EventTrackerStatus} which work just has started on. Can be used as a marker to react on in
 * the {@link EventTrackerStatusChangeListener}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class AddedTrackerStatus extends WrappedTrackerStatus {

    /**
     * Initializes the {@link AddedTrackerStatus} using the given {@code addedTrackerStatus}.
     *
     * @param addedTrackerStatus the added {@link EventTrackerStatus} this implementation references
     */
    public AddedTrackerStatus(EventTrackerStatus addedTrackerStatus) {
        super(addedTrackerStatus);
    }

    @Override
    public boolean trackerAdded() {
        return true;
    }

    @Override
    public String toString() {
        return "AddedTrackerStatus{" +
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
