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

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Wrapper around an {@link EventTrackerStatus}, delegating all calls to a {@code delegate}. Extend this class to
 * provide additional functionality to the delegate.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public abstract class WrappedTrackerStatus implements EventTrackerStatus {

    private final EventTrackerStatus delegate;

    /**
     * Initializes the {@link EventTrackerStatus} using the given {@code delegate}.
     *
     * @param delegate the actual {@link EventTrackerStatus} to delegate to
     */
    public WrappedTrackerStatus(EventTrackerStatus delegate) {
        this.delegate = delegate;
    }

    @Override
    public Segment getSegment() {
        return delegate.getSegment();
    }

    @Override
    public boolean isCaughtUp() {
        return delegate.isCaughtUp();
    }

    @Override
    public boolean isReplaying() {
        return delegate.isReplaying();
    }

    @Override
    public boolean isMerging() {
        return delegate.isMerging();
    }

    @Override
    public OptionalLong mergeCompletedPosition() {
        return delegate.mergeCompletedPosition();
    }

    @Override
    public TrackingToken getTrackingToken() {
        return delegate.getTrackingToken();
    }

    @Override
    public boolean isErrorState() {
        return delegate.isErrorState();
    }

    @Override
    public Throwable getError() {
        return delegate.getError();
    }

    @Override
    public OptionalLong getCurrentPosition() {
        return delegate.getCurrentPosition();
    }

    @Override
    public OptionalLong getResetPosition() {
        return delegate.getResetPosition();
    }

    @Override
    public boolean trackerAdded() {
        return delegate.trackerAdded();
    }

    @Override
    public boolean trackerRemoved() {
        return delegate.trackerRemoved();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WrappedTrackerStatus that = (WrappedTrackerStatus) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }
}
