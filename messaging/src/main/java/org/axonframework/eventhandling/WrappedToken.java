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

import java.util.Optional;

/**
 * Interface marking a token that wraps another token. As certain implementations may depend on specific token types,
 * Tokens that wrap another must provide a means to retrieve the original token.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public interface WrappedToken extends TrackingToken {

    /**
     * Extracts a raw token describing the current processing position of the given {@code token}. If the given token is
     * a wrapped token, it will be unwrapped until the raw token (as received from the event stream) is reached.
     * <p>
     * The returned token represents the minimal position described by the given token (which may express a range)
     *
     * @param token The token to unwrap
     * @return the raw lower bound token described by given token
     */
    static TrackingToken unwrapLowerBound(TrackingToken token) {
        return token instanceof WrappedToken ? ((WrappedToken) token).lowerBound() : token;
    }

    /**
     * Extracts a raw token describing the current processing position of the given {@code token}. If the given token is
     * a wrapped token, it will be unwrapped until the raw token (as received from the event stream) is reached.
     * <p>
     * The returned token represents the furthest position described by the given token (which may express a range)
     *
     * @param token The token to unwrap
     * @return the raw upper bound token described by given token
     */
    static TrackingToken unwrapUpperBound(TrackingToken token) {
        return token instanceof WrappedToken ? ((WrappedToken) token).upperBound() : token;
    }

    /**
     * Unwrap the given {@code token} until a token of given {@code tokenType} is exposed. Returns an empty optional if
     * the given {@code token} is not a WrappedToken instance, or if it does not wrap a token of expected {@code
     * tokenType}.
     *
     * @param token     The token to unwrap
     * @param tokenType The type of token to reveal
     * @param <R>       The generic type of the token to reveal
     * @return an optional with the unwrapped token, if found
     */
    static <R extends TrackingToken> Optional<R> unwrap(TrackingToken token, Class<R> tokenType) {
        if (token instanceof WrappedToken) {
            return ((WrappedToken) token).unwrap(tokenType);
        } else if (tokenType.isInstance(token)) {
            return Optional.of(tokenType.cast(token));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Advance the given {@code base} {@link TrackingToken} to the {@code target}. This method will return the {@code
     * target} as is if the {@code base} is not an implementation of a {@link WrappedToken}. If it is a {@code
     * WrappedToken}, it will invoke {@link #advancedTo(TrackingToken)} on the {@code base}, using the {@code target}.
     *
     * @param base   the {@link TrackingToken} to validate if it's a {@link WrappedToken} which can be {@link
     *               #advancedTo(TrackingToken)}
     * @param target the {@link TrackingToken} to advance the given {@code base} to
     * @return the {@code target} if {@code base} does not implement {@link WrappedToken}, otherwise the result of
     * invoking {@link #advancedTo(TrackingToken)} on the {@code base} using the {@code target}
     */
    static TrackingToken advance(TrackingToken base, TrackingToken target) {
        return base instanceof WrappedToken ? ((WrappedToken) base).advancedTo(target) : target;
    }

    /**
     * Advance this token to the given {@code newToken}.
     *
     * @param newToken The token representing the position to advance to
     * @return a token representing the new position
     */
    TrackingToken advancedTo(TrackingToken newToken);

    /**
     * Returns the token representing the current position in the stream.
     *
     * @return the token representing the current position in the stream
     */
    TrackingToken lowerBound();

    /**
     * Returns the token representing the furthest position in the stream described by this token. This is usually a
     * position that has been (partially) processed before.
     *
     * @return the token representing the furthest position reached in the stream
     */
    TrackingToken upperBound();

    /**
     * Retrieve a token of given {@code tokenType} if it is wrapped by this token.
     *
     * @param tokenType The type of token to unwrap to
     * @param <R>       The generic type of the token to unwrap to
     * @return an optional with the unwrapped token, if found
     */
    <R extends TrackingToken> Optional<R> unwrap(Class<R> tokenType);
}
