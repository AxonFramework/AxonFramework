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

package org.axonframework.axonserver.connector.processor.grpc;

import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;

import java.util.OptionalLong;

/**
 * Created by Sara Pellegrini on 01/08/2018.
 * sara.pellegrini@gmail.com
 */
public class FakeEventTrackerStatus implements EventTrackerStatus {

    private final Segment segment;
    private final boolean caughtUp;
    private final boolean replaying;
    private final boolean merging;
    private final TrackingToken trackingToken;
    private final Throwable exception;

    public FakeEventTrackerStatus(Segment segment, boolean caughtUp, boolean replaying,
                                  boolean merging, TrackingToken trackingToken, Throwable exception) {
        this.segment = segment;
        this.caughtUp = caughtUp;
        this.replaying = replaying;
        this.merging = merging;
        this.trackingToken = trackingToken;
        this.exception = exception;
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
        return replaying;
    }

    @Override
    public boolean isMerging() {
        return merging;
    }

    @Override
    public OptionalLong mergeCompletedPosition() {
        return OptionalLong.empty();
    }

    @Override
    public TrackingToken getTrackingToken() {
        return trackingToken;
    }

    @Override
    public boolean isErrorState() {
        return exception != null;
    }

    @Override
    public Throwable getError() {
        return exception;
    }

    @Override
    public OptionalLong getCurrentPosition() {
        return (trackingToken != null) ? trackingToken.position() : OptionalLong.empty();
    }

    @Override
    public OptionalLong getResetPosition() {
        return ReplayToken.getTokenAtReset(trackingToken);
    }
}
