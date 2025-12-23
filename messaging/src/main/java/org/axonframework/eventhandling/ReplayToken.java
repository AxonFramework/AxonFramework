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
        if (startPosition != null && !startPosition.equalsLatest(tokenAtReset) && startPosition.covers(WrappedToken.unwrapLowerBound(tokenAtReset))) {
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
        if (replayIsComplete(newToken)) {
            // we're done replaying
            // if the token at reset was a wrapped token itself, we'll need to use that one to maintain progress.
            if (tokenAtReset instanceof WrappedToken) {
                return ((WrappedToken) tokenAtReset).advancedTo(newToken);
            }
            return newToken;
        }
        if (wasDeliveredBeforeReset(newToken)) { // if we had this event before
            // we're still well behind
            return new ReplayToken(tokenAtReset, newToken, context, true);
        }
        // we're getting an event that we didn't have before, but we haven't finished replaying either
        if (tokenAtReset instanceof WrappedToken) {
            return new ReplayToken(tokenAtReset.upperBound(newToken),
                    ((WrappedToken) tokenAtReset).advancedTo(newToken),
                    context,
                    false);
        }
        return new ReplayToken(tokenAtReset.upperBound(newToken), newToken, context, false);
    }

    /**
     * Determines if the replay phase is complete and we should exit the ReplayToken wrapper.
     * <p>
     * Replay is complete when BOTH conditions are met:
     * <ol>
     *   <li><b>Caught up:</b> newToken is strictly ahead of tokenAtReset's upper bound</li>
     *   <li><b>Not covered:</b> tokenAtReset does not cover newToken's position</li>
     * </ol>
     * <p>
     * <b>Why both conditions?</b>
     * <p>
     * For simple tokens (e.g., {@link GapAwareTrackingToken}), checking "caught up" alone would suffice.
     * However, for {@link MergedTrackingToken}, we compare against the <i>upper</i> segment's bound,
     * but must also verify the <i>full</i> token doesn't still cover the position (which could happen
     * if segments have different states).
     * <p>
     * The {@code covers()} check ensures we don't prematurely exit replay when tokenAtReset
     * still considers the position as "within its tracking scope."
     */
    private boolean replayIsComplete(TrackingToken newToken) {
        if (tokenAtReset == null) {
            return true;
        }
        TrackingToken resetUpperBound = WrappedToken.unwrapUpperBound(tokenAtReset);
        boolean caughtUpToResetPosition = isStrictlyAhead(newToken, resetUpperBound);
        return caughtUpToResetPosition && !wasDeliveredBeforeReset(newToken);
    }

    /**
     * Determines if the event represented by newToken was delivered before the reset occurred.
     * <p>
     * This method leverages the {@link TrackingToken#lowerBound(TrackingToken)} behavior to detect
     * whether an event was already processed or was skipped (e.g., not yet committed) at reset time.
     * <p>
     * <b>How it works:</b>
     * <p>
     * The {@code lowerBound()} method computes the "earliest common position" of two tokens.
     * For {@link GapAwareTrackingToken}, when the minimum index falls on a gap, the algorithm
     * walks backwards to find the first non-gap position.
     * <p>
     * This "walk back" behavior naturally distinguishes:
     * <ul>
     *   <li><b>Events that were delivered:</b> {@code lowerBound} stays at the same index as newToken
     *       → {@code combinedLowerBound.equalsLatest(newToken)} returns {@code true}</li>
     *   <li><b>Events that were skipped:</b> {@code lowerBound} walks back past the gap
     *       → {@code combinedLowerBound.equalsLatest(newToken)} returns {@code false}</li>
     * </ul>
     * <p>
     * <b>Example:</b>
     * <pre>
     * tokenAtReset: index=10, gaps=[7, 8]  (events 0-6 delivered, 7-8 skipped, 9-10 delivered)
     *
     * Case 1: newToken at index 5
     *   → lowerBound computes index 5 (not a gap, stays)
     *   → equalsLatest(newToken)? YES → was delivered before reset
     *
     * Case 2: newToken at index 7
     *   → lowerBound computes index 7, but 7 is a gap → walks back to 6
     *   → equalsLatest(newToken)? NO (6 ≠ 7) → was NOT delivered before reset (it's new!)
     * </pre>
     *
     * @param newToken the token representing the current event position
     * @return {@code true} if the event was delivered before reset (replay), {@code false} if it's a new event
     * @see GapAwareTrackingToken#lowerBound(TrackingToken)
     */
    private boolean wasDeliveredBeforeReset(TrackingToken newToken) {
        TrackingToken resetLowerBound = WrappedToken.unwrapLowerBound(tokenAtReset);
        TrackingToken newTokenLowerBound = WrappedToken.unwrapLowerBound(newToken);
        TrackingToken combinedLowerBound = resetLowerBound.lowerBound(newTokenLowerBound);
        return combinedLowerBound.equalsLatest(newTokenLowerBound);
    }

    private static boolean isStrictlyAhead(@Nullable TrackingToken token, @Nullable TrackingToken other) {
        return token != null && other != null
                && !other.equalsLatest(token)
                && token.covers(other);
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
    public boolean equalsLatest(TrackingToken other) {
        if (other instanceof ReplayToken) {
            return currentToken != null && currentToken.equalsLatest(((ReplayToken) other).currentToken);
        }
        return currentToken != null && currentToken.equalsLatest(other);
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
