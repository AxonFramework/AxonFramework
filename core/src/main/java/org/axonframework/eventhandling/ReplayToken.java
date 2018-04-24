/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.Message;

import java.util.Objects;

/**
 * Token keeping track of the position before a reset was triggered. This allows for downstream components to detect
 * messages that are redelivered ass part of a replay.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public class ReplayToken implements TrackingToken, WrappedToken {

    private final TrackingToken tokenAtReset;
    private final TrackingToken currentToken;

    /**
     * Initialize a ReplayToken, using the given {@code tokenAtReset} to represent the position at which a reset was
     * triggered. The current token is reset to the initial position.
     * <p>
     * Using the {@link #createReplayToken(TrackingToken)} is preferred, as it covers cases where a replay is started
     *
     * @param tokenAtReset The token representing the position at which the reset was triggered.
     */
    public ReplayToken(TrackingToken tokenAtReset) {
        this.tokenAtReset = tokenAtReset;
        this.currentToken = null;
    }

    protected ReplayToken(TrackingToken tokenAtReset, TrackingToken newRedeliveryToken) {
        this.tokenAtReset = tokenAtReset;
        this.currentToken = newRedeliveryToken;
    }

    /**
     * Creates a new TrackingToken that reflects the reset state, when appropriate.
     *
     * @param tokenAtReset
     * @return
     */
    public static TrackingToken createReplayToken(TrackingToken tokenAtReset) {
        if (tokenAtReset == null) {
            // we haven't processed anything, so there is no need for a reset token
            return null;
        }
        if (tokenAtReset instanceof ReplayToken) {
            return createReplayToken(((ReplayToken) tokenAtReset).tokenAtReset);
        }
        return new ReplayToken(tokenAtReset);
    }

    public static boolean isReplay(Message<?> message) {
        return message instanceof TrackedEventMessage
                && isReplay(((TrackedEventMessage) message).trackingToken());
    }

    public static boolean isReplay(TrackingToken trackingToken) {
        return trackingToken instanceof ReplayToken
                && ((ReplayToken) trackingToken).isReplay();

    }

    public TrackingToken advancedTo(TrackingToken newToken) {
        if (this.tokenAtReset == null
                || (newToken.covers(this.tokenAtReset) && !tokenAtReset.covers(newToken))) {
            // we're done replaying
            return newToken;
        } else if (tokenAtReset.covers(newToken)) {
            // we're still well behind
            return new ReplayToken(tokenAtReset, newToken);
        } else {
            // we're getting an event that we didn't have before, but we haven't finished replaying either
            return new ReplayToken(tokenAtReset.upperBound(newToken), newToken);
        }
    }

    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        if (other instanceof ReplayToken) {
            return new ReplayToken(this, ((ReplayToken) other).currentToken);
        }
        return new ReplayToken(this, other);
    }

    @Override
    public TrackingToken upperBound(TrackingToken other) {
        return advancedTo(other);
    }

    @Override
    public boolean covers(TrackingToken other) {
        if (other instanceof ReplayToken) {
            return currentToken != null && currentToken.covers(((ReplayToken) other).currentToken);
        }
        return currentToken != null && currentToken.covers(other);
    }

    private boolean isReplay() {
        return currentToken == null || tokenAtReset.covers(currentToken);
    }

    @Override
    public TrackingToken unwrap() {
        return currentToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ReplayToken that = (ReplayToken) o;
        return Objects.equals(tokenAtReset, that.tokenAtReset) &&
                Objects.equals(currentToken, that.currentToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenAtReset, currentToken);
    }

    @Override
    public String toString() {
        return "ReplayToken{" +
                "currentToken=" + currentToken +
                ", tokenAtReset=" + tokenAtReset +
                '}';
    }
}
