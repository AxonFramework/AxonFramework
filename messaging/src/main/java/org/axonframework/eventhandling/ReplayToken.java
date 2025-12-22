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
        if (startPosition != null && tokenAtReset != null
                && startPosition.covers(WrappedToken.unwrapLowerBound(tokenAtReset))
                && !tokenAtReset.covers(startPosition)) {
            return startPosition;
        }
        return createReplayToken(startPosition != null && tokenAtReset != null ? tokenAtReset.upperBound(startPosition) : tokenAtReset, startPosition, null);
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
        // (startPosition covers tokenAtReset AND tokenAtReset does NOT cover startPosition)
        // When they're at the same position, we still need a ReplayToken
        if (startPosition != null
                && startPosition.covers(WrappedToken.unwrapLowerBound(tokenAtReset))
                && !tokenAtReset.covers(startPosition)) {
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
     * Note: This method requires instanceof checks for GapAwareTrackingToken because the standard
     * {@link TrackingToken#covers(TrackingToken)} method has asymmetric semantics when gaps differ,
     * making it impossible to distinguish "same index, different gaps" from "strictly beyond"
     * using covers() alone. A future improvement could add a {@code rawIndex()} method to the
     * TrackingToken interface to enable polymorphic index comparison.
     */
    private static boolean isStrictlyBeyondResetPosition(TrackingToken tokenAtReset, TrackingToken newToken) {
        // Use upperBound to get the "furthest ahead" position we need to catch up to
        // This is important for MergedTrackingToken where we need to be beyond ALL segments
        TrackingToken rawAtReset = WrappedToken.unwrapUpperBound(tokenAtReset);
        TrackingToken rawNew = WrappedToken.unwrapLowerBound(newToken);

        // For GapAwareTrackingToken, we must compare indices directly because covers() has
        // asymmetric behavior when gaps differ (same index with fewer gaps is incorrectly
        // considered "beyond" by the covers() check alone)
        if (rawAtReset instanceof GapAwareTrackingToken && rawNew instanceof GapAwareTrackingToken) {
            return ((GapAwareTrackingToken) rawNew).getIndex() > ((GapAwareTrackingToken) rawAtReset).getIndex();
        }

        // For other token types, use covers-based logic
        // (strictly beyond means newToken covers resetToken AND resetToken does NOT cover newToken)
        return rawNew.covers(rawAtReset) && !rawAtReset.covers(rawNew);
    }

    /**
     * Determines if the event represented by newToken is a "new event" (not previously processed).
     * An event is new if:
     * 1. Its index is beyond the reset position (for the lowerBound segment), OR
     * 2. Its index was a gap in the tokenAtReset
     *
     * This is used to correctly set lastMessageWasReplay when processing events during replay.
     * For MergedTrackingToken, we use lowerBound because the lower segment is the one "catching up"
     * and events beyond its position are new to it.
     */
    private static boolean isNewEventForResetPosition(TrackingToken tokenAtReset, TrackingToken newToken) {
        // Handle MultiSourceTrackingToken specially
        if (tokenAtReset instanceof MultiSourceTrackingToken && newToken instanceof MultiSourceTrackingToken) {
            return isNewEventForMultiSource((MultiSourceTrackingToken) tokenAtReset,
                                             (MultiSourceTrackingToken) newToken);
        }

        // Unwrap both tokens to get to the raw tracking tokens
        // Use lowerBound for tokenAtReset because in a merge, the lower segment is catching up
        TrackingToken rawAtReset = WrappedToken.unwrapLowerBound(tokenAtReset);
        TrackingToken rawNew = WrappedToken.unwrapLowerBound(newToken);

        if (rawAtReset instanceof GapAwareTrackingToken && rawNew instanceof GapAwareTrackingToken) {
            GapAwareTrackingToken gatAtReset = (GapAwareTrackingToken) rawAtReset;
            GapAwareTrackingToken gatNew = (GapAwareTrackingToken) rawNew;
            // Event is "new" (not a replay) if:
            // 1. Index is beyond the reset position (wasn't processed before), OR
            // 2. Index was a gap in the reset position (wasn't processed before)
            return gatNew.getIndex() > gatAtReset.getIndex()
                    || gatAtReset.getGaps().contains(gatNew.getIndex());
        }

        // For non-GapAwareTrackingToken types, if tokenAtReset doesn't cover newToken,
        // it's likely a new event (conservative approach)
        return true;
    }

    /**
     * For MultiSourceTrackingToken, checks if the event is new for ANY source.
     * An event is considered a replay only if ALL sources have already processed it.
     */
    private static boolean isNewEventForMultiSource(MultiSourceTrackingToken tokenAtReset,
                                                     MultiSourceTrackingToken newToken) {
        // For each source in the token, check if the event is new
        // If new for ANY source, return true (it's a new event)
        for (Map.Entry<String, TrackingToken> entry : newToken.getTrackingTokens().entrySet()) {
            String sourceName = entry.getKey();
            TrackingToken newSourceToken = entry.getValue();
            TrackingToken resetSourceToken = tokenAtReset.getTokenForStream(sourceName);

            if (newSourceToken == null || resetSourceToken == null) {
                continue;
            }

            // Check if this source considers the event as new
            if (isNewEventForSingleSource(resetSourceToken, newSourceToken)) {
                return true; // New for at least one source
            }
        }
        return false; // Not new for any source (it's a replay)
    }

    /**
     * Helper to check if an event is new for a single source token.
     */
    private static boolean isNewEventForSingleSource(TrackingToken resetToken, TrackingToken newToken) {
        TrackingToken rawReset = WrappedToken.unwrapLowerBound(resetToken);
        TrackingToken rawNew = WrappedToken.unwrapLowerBound(newToken);

        if (rawReset instanceof GapAwareTrackingToken && rawNew instanceof GapAwareTrackingToken) {
            GapAwareTrackingToken gatReset = (GapAwareTrackingToken) rawReset;
            GapAwareTrackingToken gatNew = (GapAwareTrackingToken) rawNew;
            return gatNew.getIndex() > gatReset.getIndex()
                    || gatReset.getGaps().contains(gatNew.getIndex());
        }

        // For non-GAT tokens, check using covers
        return !resetToken.covers(newToken);
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
