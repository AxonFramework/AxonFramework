/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.Message;

import java.time.Instant;

/**
 * @author Rene de Waele
 */
public class GenericTrackedEventMessage<T> extends GenericEventMessage<T> implements TrackedEventMessage<T> {
    private final TrackingToken trackingToken;

    public GenericTrackedEventMessage(TrackingToken trackingToken, EventMessage<T> delegate) {
        super(delegate, delegate.getTimestamp());
        this.trackingToken = trackingToken;
    }

    public GenericTrackedEventMessage(TrackingToken trackingToken, Message<T> delegate, Instant timestamp) {
        super(delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }
}
