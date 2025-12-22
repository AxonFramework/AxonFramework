/*
 * Copyright (c) 2010-2022. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.axonframework.messaging.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Token keeping track of the position before a reset was triggered. This allows for downstream components to detect
 * messages that are redelivered as part of a replay.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public class ReplayToken implements TrackingToken, WrappedToken, Serializable {

    private static final long serialVersionUID = -4102464856247630944L;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private final TrackingToken tokenAtReset;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private final TrackingToken currentToken;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private final Object context;
    private final transient boolean lastMessageWasReplay;

    /**
     * Initialize a ReplayToken, using the given {@code tokenAtReset} to represent the position at which a reset was
     * triggered. The current token is reset to the initial position.
     * <p>
     * Using the {@link #createReplayToken(TrackingToken)} is preferred, as it executes sanity checks on the
     * parameters.
     *
     * @param tokenAtReset The token representing the position at which the reset was triggered.
     * @deprecated Use the {@link #createReplayToken(TrackingToken)} method instead.
     */
    @Deprecated
    public ReplayToken(TrackingToken tokenAtReset) {
        this(tokenAtReset, null, null);
    }

    /**
     * Initializes a ReplayToken with {@code tokenAtReset} which represents the position at which a reset was triggered
     * and the {@code newRedeliveryToken} which represents current token.
     * <p>
     * Using the {@link #createReplayToken(TrackingToken, TrackingToken)} is preferred, as it executes sanity checks on
     * the parameters.
     *
     * @param tokenAtReset       The token representing the position at which the reset was triggered
     * @param newRedeliveryToken The current token
     * @deprecated Use the {@link #createReplayToken(TrackingToken, TrackingToken)} method instead.
     */
    @Deprecated
    public ReplayToken(TrackingToken tokenAtReset,
                       TrackingToken newRedeliveryToken) {
        this(tokenAtReset, newRedeliveryToken, null, true);
    }

    /**
     * Initializes a ReplayToken with {@code tokenAtReset} which represents the position at which a reset was triggered
     * and the {@code newRedeliveryToken} which represents current token.
     *
     * @param tokenAtReset       The token representing the position at which the reset was triggered
     * @param newRedeliveryToken The current token
     * @param resetContext       The reset context of the replay
     */
    @JsonCreator
    @ConstructorProperties({"tokenAtReset", "currentToken", "resetContext"})
    ReplayToken(@JsonProperty("tokenAtReset") TrackingToken tokenAtReset,
                @JsonProperty("currentToken") TrackingToken newRedeliveryToken,
                @JsonProperty("resetContext") Object resetContext) {
        this(tokenAtReset, newRedeliveryToken, resetContext, true);
    }

    private ReplayToken(TrackingToken tokenAtReset,
                        TrackingToken newRedeliveryToken,
                        Object context,
                        boolean lastMessageWasReplay
    ) {
        this.tokenAtReset = tokenAtReset;
        this.currentToken = newRedeliveryToken;
        this.context = context;
        this.lastMessageWasReplay = lastMessageWasReplay;
    }

    /**
     * Indicates whether the given message is "redelivered", as a result of a previous reset. If {@code true}, this
     * means this message has been delivered to this processor before its token was reset.
     *
     * @param message The message to inspect
     * @return {@code true} if the message is a replay
     */
    public static boolean isReplay(Message<?> message) {
        return message instanceof TrackedEventMessage
                && isReplay(((TrackedEventMessage) message).trackingToken());
    }

    /**
     * Creates a new TrackingToken that represents the given {@code startPosition} of a stream. It will be in replay
     * state until the position of the provided {@code tokenAtReset}. After that, the {@code tokenAtReset} will become
     * the active token and the stream will no longer be considered as replaying.
     *
     * @param tokenAtReset  The token present when the reset was triggered
     * @param startPosition The position where the token should be reset to and start replaying from
     * @return A token that represents a reset to the {@code startPosition} until the provided {@code tokenAtReset}
     */
    public static TrackingToken createReplayToken(TrackingToken tokenAtReset, @Nullable TrackingToken startPosition) {
        // If startPosition is strictly ahead of tokenAtReset, no replay is needed
        TrackingToken unwrappedTokenAtReset = WrappedToken.unwrapLowerBound(tokenAtReset);
        if (startPosition != null && unwrappedTokenAtReset != null
                && !unwrappedTokenAtReset.same(startPosition)
                && startPosition.covers(unwrappedTokenAtReset)) {
            return startPosition;
        }
        return createReplayToken(tokenAtReset, startPosition, null);
    }

    /**
     * Creates a new TrackingToken that represents the given {@code startPosition} of a stream. It will be in replay
     * state until the position of the provided {@code tokenAtReset}. After that, the {@code tokenAtReset} will become
     * the active token and the stream will no longer be considered as replaying.
     *
     * @param tokenAtReset  The token present when the reset was triggered
     * @param startPosition The position where the token should be reset to and start replaying from
     * @param resetContext  The context given to the reset, may be null
     * @return A token that represents a reset to the {@code startPosition} until the provided {@code tokenAtReset}
     */
    public static TrackingToken createReplayToken(
            TrackingToken tokenAtReset,
            TrackingToken startPosition,
            Object resetContext
    ) {
        if (tokenAtReset == null) {
            return startPosition;
        }
        if (tokenAtReset instanceof ReplayToken) {
            return createReplayToken(((ReplayToken) tokenAtReset).tokenAtReset, startPosition, resetContext);
        }
        // Only skip replay if startPosition is STRICTLY ahead of tokenAtReset
        // Use same() to ensure tokens are NOT at the same position before considering strictly ahead
        TrackingToken unwrappedTokenAtReset = WrappedToken.unwrapLowerBound(tokenAtReset);
        if (startPosition != null
                && !unwrappedTokenAtReset.same(startPosition)
                && startPosition.covers(unwrappedTokenAtReset)) {
            return startPosition;
        }
        return new ReplayToken(tokenAtReset, startPosition, resetContext);
    }

    /**
     * Creates a new TrackingToken that represents the tail of the stream. It will be in replay state until the position
     * of the provided {@code tokenAtReset}. After that, the {@code tokenAtReset} will become the active token and the
     * stream will no longer be considered as replaying.
     *
     * @param tokenAtReset The token present when the reset was triggered
     * @return A token that represents a reset to the tail of the stream
     */
    public static TrackingToken createReplayToken(TrackingToken tokenAtReset) {
        return createReplayToken(tokenAtReset, null);
    }

    /**
     * Extracts the context from a {@code message} of the matching {@code contextClass}.
     * <p>
     * Will resolve to an empty {@code Optional} if the provided message is not a {@link TrackedEventMessage}, doesn't
     * contain a {@link ReplayToken}, or the context isn't an instance of the provided {@code contextClass}, or there is
     * no context at all.
     *
     * @param message      The message to extract the context from
     * @param contextClass The class the context should match
     * @param <T>          The type of the context
     * @return The context, if present in the message
     */
    public static <T> Optional<T> replayContext(EventMessage<?> message, @Nonnull Class<T> contextClass) {
        if (message instanceof TrackedEventMessage) {
            return replayContext(((TrackedEventMessage<?>) message).trackingToken(), contextClass);
        }
        return Optional.empty();
    }

    /**
     * Indicates whether the given {@code trackingToken} represents a position that is part of a replay.
     *
     * @param trackingToken The token to verify
     * @return {@code true} if the token indicates a replay
     */
    public static boolean isReplay(TrackingToken trackingToken) {
        return WrappedToken.unwrap(trackingToken, ReplayToken.class)
                .map(rt -> rt.isReplay())
                .orElse(false);
    }

    /**
     * Extracts the context from a {@code message} of the matching {@code contextClass}.
     * <p>
     * Will resolve to an empty {@code Optional} if the provided token is not a {@link ReplayToken}, or the context
     * isn't an instance of the provided {@code contextClass}, or there is no context at all.
     *
     * @param trackingToken The tracking token to extract the context from
     * @param contextClass  The class the context should match
     * @param <T>           The type of the context
     * @return The context, if present in the token
     */
    public static <T> Optional<T> replayContext(TrackingToken trackingToken, @Nonnull Class<T> contextClass) {
        return WrappedToken.unwrap(trackingToken, ReplayToken.class)
                .map(ReplayToken::context)
                .filter(c -> c.getClass().isAssignableFrom(contextClass))
                .map(contextClass::cast);
    }

    /**
     * Return the relative position at which a reset was triggered for this Segment. In case a replay finished or no
     * replay is active, an {@code OptionalLong.empty()} will be returned.
     *
     * @return the relative position at which a reset was triggered for this token
     */
    public static OptionalLong getTokenAtReset(TrackingToken trackingToken) {
        return WrappedToken.unwrap(trackingToken, ReplayToken.class)
                .map(rt -> rt.getTokenAtReset().position())
                .orElse(OptionalLong.empty());
    }

    /**
     * Gets the token representing the position at which the reset was triggered.
     *
     * @return the token representing the position at which the reset was triggered
     */
    public TrackingToken getTokenAtReset() {
        return tokenAtReset;
    }

    /**
     * Gets the current token.
     *
     * @return the current token
     */
    public TrackingToken getCurrentToken() {
        return currentToken;
    }

    @Override
    public TrackingToken advancedTo(TrackingToken newToken) {
        if (this.tokenAtReset == null
                || (isStrictlyBeyondResetPosition(tokenAtReset, newToken)
                && newToken.covers(WrappedToken.unwrapUpperBound(this.tokenAtReset))
                && !tokenAtReset.covers(WrappedToken.unwrapLowerBound(newToken)))) {
            // we're done replaying - newToken is strictly beyond tokenAtReset
            // if the token at reset was a wrapped token itself, we'll need to use that one to maintain progress.
            if (tokenAtReset instanceof WrappedToken) {
                return ((WrappedToken) tokenAtReset).advancedTo(newToken);
            }
            return newToken;
        } else if (tokenAtReset.covers(WrappedToken.unwrapLowerBound(newToken))) {
            // we're still well behind
            return new ReplayToken(tokenAtReset, newToken, context, true);
        } else {
            // We're in replay territory but tokenAtReset.covers(newToken) failed.
            // This happens when newToken has different gaps than tokenAtReset (e.g., gaps filled during replay)
            // OR when newToken's index is beyond tokenAtReset's lowerBound but not its upperBound (merge scenario).
            // We need to determine if the event at newToken's index was processed before reset:
            // - If it's a NEW event (beyond index or was a gap): lastMessageWasReplay = false
            // - If it was processed before reset: lastMessageWasReplay = true
            boolean isNewEvent = isNewEventForResetPosition(tokenAtReset, newToken);

            if (tokenAtReset instanceof WrappedToken) {
                return new ReplayToken(tokenAtReset.upperBound(newToken),
                        ((WrappedToken) tokenAtReset).advancedTo(newToken),
                        context,
                        !isNewEvent);
            }
            return new ReplayToken(tokenAtReset.upperBound(newToken), newToken, context, !isNewEvent);
        }
    }

    /**
     * Determines if newToken is strictly beyond tokenAtReset's position.
     * Uses upperBound to handle MergedTrackingToken (we're "done" when beyond the furthest segment).
     * <p>
     * Uses {@link TrackingToken#same(TrackingToken)} instead of {@link TrackingToken#covers(TrackingToken)}
     * because covers() has asymmetric semantics for GapAwareTrackingToken when gaps differ,
     * making it impossible to distinguish "same index, different gaps" from "strictly beyond".
     * The same() method checks for positional equality without the gap containment requirement.
     */
    private static boolean isStrictlyBeyondResetPosition(TrackingToken tokenAtReset, TrackingToken newToken) {
        // Use upperBound to get the "furthest ahead" position we need to catch up to
        // This is important for MergedTrackingToken where we need to be beyond ALL segments
        TrackingToken rawAtReset = WrappedToken.unwrapUpperBound(tokenAtReset);
        TrackingToken rawNew = WrappedToken.unwrapLowerBound(newToken);

        // Strictly beyond means: not at the same position AND newToken covers the reset position.
        // The same() method checks positional equality (index comparison without gap containment),
        // so !same() with covers() gives us "strictly ahead" semantics.
        // Since event processing only moves forward, !same() implies we're ahead.
        return !rawAtReset.same(rawNew) && rawNew.covers(rawAtReset);
    }

    /**
     * Determines if the event represented by newToken is a "new event" (not previously processed).
     * An event is new if:
     * 1. Its index is beyond the reset position, OR
     * 2. Its index was a gap in the tokenAtReset (gap filled during replay)
     * <p>
     * This uses the {@link TrackingToken#lowerBound(TrackingToken)} method to normalize comparison.
     * For GapAwareTrackingToken, lowerBound's calculateIndex() decrements through gaps, so if
     * newToken's index was a gap in tokenAtReset, the lowerBound's index will be less than
     * newToken's index, making same() return false (indicating a new event).
     * <p>
     * This is used to correctly set lastMessageWasReplay when processing events during replay.
     */
    private static boolean isNewEventForResetPosition(TrackingToken tokenAtReset, TrackingToken newToken) {
        // Unwrap both tokens to get to the raw tracking tokens
        // Use lowerBound for wrapped tokens because in a merge, the lower segment is catching up
        TrackingToken rawAtReset = WrappedToken.unwrapLowerBound(tokenAtReset);
        TrackingToken rawNew = WrappedToken.unwrapLowerBound(newToken);

        // Use lowerBound() to normalize the comparison:
        // - For GapAwareTrackingToken: lowerBound merges gaps and uses calculateIndex() which
        //   decrements through gaps. If newToken's index was a gap, lowerBound's index < newToken's index.
        // - For GlobalSequenceTrackingToken: lowerBound returns the token with lower index.
        // Then same() checks if positions are equal - if not, it's a new event.
        TrackingToken lowerBound = rawAtReset.lowerBound(rawNew);
        return !lowerBound.same(rawNew);
    }

    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        if (other instanceof ReplayToken) {
            return new ReplayToken(this, ((ReplayToken) other).currentToken, context);
        }
        return new ReplayToken(this, other, context);
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

    @Override
    public boolean same(TrackingToken other) {
        if (other instanceof ReplayToken) {
            return currentToken != null && currentToken.same(((ReplayToken) other).currentToken);
        }
        return currentToken != null && currentToken.same(other);
    }

    private boolean isReplay() {
        return lastMessageWasReplay;
    }

    @Override
    public TrackingToken lowerBound() {
        return WrappedToken.unwrapLowerBound(currentToken);
    }

    @Override
    public TrackingToken upperBound() {
        return WrappedToken.unwrapUpperBound(currentToken);
    }

    @Override
    public <R extends TrackingToken> Optional<R> unwrap(Class<R> tokenType) {
        if (tokenType.isInstance(this)) {
            return Optional.of(tokenType.cast(this));
        } else {
            return WrappedToken.unwrap(currentToken, tokenType);
        }
    }

    /**
     * Returns the context that was provided when the token was reset.
     *
     * @return The context, null if none was provided during reset.
     */
    @Nullable
    public Object context() {
        return context;
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
                Objects.equals(currentToken, that.currentToken) &&
                Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenAtReset, currentToken, context);
    }

    @Override
    public String toString() {
        return "ReplayToken{" +
                "currentToken=" + currentToken +
                ", tokenAtReset=" + tokenAtReset +
                ", context=" + (context != null ? context.toString() : null) +
                '}';
    }

    @Override
    public OptionalLong position() {
        if (currentToken != null) {
            return currentToken.position();
        }
        // if we don't have a currentToken, we assume we're at the start
        return OptionalLong.of(0);
    }
}
