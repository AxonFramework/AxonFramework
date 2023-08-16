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

import org.axonframework.messaging.Message;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Generic implementation of a {@link TrackedEventMessage}.
 *
 * @param <T> The type of payload contained in this Message
 */
public class GenericTrackedEventMessage<T> extends GenericEventMessage<T> implements TrackedEventMessage<T> {
    private static final long serialVersionUID = -8955035317050776846L;
    private final TrackingToken trackingToken;

    /**
     * Creates a GenericTrackedEventMessage with given {@code trackingToken} and delegate event message.
     *
     * @param trackingToken tracking token of the event
     * @param delegate      delegate message containing payload, metadata, identifier and timestamp
     */
    public GenericTrackedEventMessage(TrackingToken trackingToken, EventMessage<T> delegate) {
        super(delegate, delegate.getTimestamp());
        this.trackingToken = trackingToken;
    }

    /**
     * Creates a GenericTrackedEventMessage with given {@code trackingToken} and delegate event message. The timestamp
     * of the event is supplied lazily to prevent unnecessary deserialization of the timestamp.
     *
     * @param trackingToken tracking token of the event
     * @param delegate      delegate message containing payload, metadata, identifier and timestamp
     * @param timestamp     supplier of the message's timestamp
     */
    public GenericTrackedEventMessage(TrackingToken trackingToken, Message<T> delegate, Supplier<Instant> timestamp) {
        super(delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    /**
     * Initializes a {@link GenericTrackedEventMessage} with given message as delegate and given {@code timestamp}. The
     * given message will be used supply the payload, metadata and identifier of the resulting event message.
     *
     * @param trackingToken the tracking token of the resulting message
     * @param delegate      the message that will be used used as delegate
     * @param timestamp     the timestamp of the resulting event message
     */
    protected GenericTrackedEventMessage(TrackingToken trackingToken, Message<T> delegate, Instant timestamp) {
        super(delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", trackingToken={")
                     .append(trackingToken())
                     .append('}');
    }

    @Override
    public GenericTrackedEventMessage<T> withTrackingToken(TrackingToken trackingToken) {
        return new GenericTrackedEventMessage<>(trackingToken, this);
    }

    @Override
    protected String describeType() {
        return "GenericTrackedEventMessage";
    }
}
