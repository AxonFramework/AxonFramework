/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Context;
import org.axonframework.messaging.Context.ResourceKey;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Tag interface identifying a token that is used to identify the position of an event in an event stream. Event
 * processors use this token to keep track of the events they have processed and still need to process.
 *
 * @author Rene de Waele
 */
public interface TrackingToken {

    // TODO Discuss if we see away around having the ResourceKey here, assuming we want the add/from methods to reside here.
    /**
     * The {@link ResourceKey} used whenever a {@link Context} would contain a {@link TrackingToken}.
     */
    ResourceKey<TrackingToken> RESOURCE_KEY = ResourceKey.sharedKey(TrackingToken.class);

    /**
     * Adds the given {@code token} to the given {@code context} using the {@link #RESOURCE_KEY}.
     *
     * @param context The {@link Context} to add the given {@code token} to.
     * @param token   The {@link TrackingToken} to add to the given {@code context} using the {@link #RESOURCE_KEY}.
     */
    static Context addToContext(Context context, TrackingToken token) {
        return context.withResource(RESOURCE_KEY, token);
    }

    /**
     * Returns an {@link Optional} of {@link TrackingToken}, returning the resource keyed under the
     * {@link #RESOURCE_KEY} in the given {@code context}.
     *
     * @param context The {@link Context} to retrieve the {@link TrackingToken} from, if present.
     * @return An {@link Optional} of {@link TrackingToken}, returning the resource keyed under the
     * {@link #RESOURCE_KEY} in the given {@code context}.
     */
    static Optional<TrackingToken> fromContext(Context context) {
        return Optional.ofNullable(context.getResource(RESOURCE_KEY));
    }

    /**
     * Returns a token that represents the lower bound between this and the {@code other} token. Effectively, the
     * returned token will cause messages not received by both this and the {@code other} token to be redelivered.
     *
     * @param other The token to compare to this one
     * @return The token representing the lower bound of the two
     */
    TrackingToken lowerBound(TrackingToken other);

    /**
     * Returns the token that represents the furthest possible position in a stream that either this token or the given
     * {@code other} represents. Effectively, this means this token will only deliver messages that neither this, nor
     * the other have been received.
     *
     * @param other The token to compare this token to
     * @return a token that represents the furthest position of this or the other stream
     */
    TrackingToken upperBound(TrackingToken other);

    /**
     * Indicates whether this token covers the {@code other} token completely. That means that this token represents a
     * position in a stream that has received all of the messages that a stream represented by the {@code other} token
     * has received.
     * <p>
     * Note that this operation is only safe when comparing tokens obtained from messages from the same
     * {@link org.axonframework.messaging.StreamableMessageSource}.
     *
     * @param other The token to compare to this one
     * @return {@code true} if this token covers the other, otherwise {@code false}
     */
    boolean covers(TrackingToken other);

    /**
     * Return the estimated relative position this token represents. In case no estimation can be given an
     * {@code OptionalLong.empty()} will be returned.
     *
     * @return the estimated relative position of this token
     */
    default OptionalLong position() {
        return OptionalLong.empty();
    }
}
