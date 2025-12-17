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
 * <h2>Overview</h2>
 * <p>
 * A ReplayToken wraps two tokens:
 * <ul>
 *   <li>{@code tokenAtReset} - The position when reset was triggered (the "high water mark")</li>
 *   <li>{@code currentToken} - The current processing position during replay</li>
 * </ul>
 *
 * <h2>Key Concepts</h2>
 * <p>
 * <b>Replay Detection:</b> The {@link #advancedTo(TrackingToken)} method determines if each event is:
 * <ul>
 *   <li><b>Replay</b> - Event was seen before reset ({@code tokenAtReset.covers(newToken) = true})</li>
 *   <li><b>New</b> - Event was NOT seen before reset ({@code tokenAtReset.covers(newToken) = false})</li>
 * </ul>
 *
 * <h2>The Three Cases in advancedTo()</h2>
 * <pre>
 * CASE 1: DONE REPLAYING
 *   Condition: newToken.covers(tokenAtReset) AND !tokenAtReset.covers(newToken)
 *   Result: Exit replay mode, return unwrapped newToken
 *
 * CASE 2: STILL REPLAYING
 *   Condition: tokenAtReset.covers(newToken)
 *   Result: Return ReplayToken with lastMessageWasReplay=true
 *
 * CASE 3: PARTIAL CATCH-UP (gaps)
 *   Condition: Neither Case 1 nor Case 2
 *   Result: Return ReplayToken with lastMessageWasReplay=false
 *           (new event during replay - happens when filling gaps)
 * </pre>
 *
 * <h2>Why Case 1 Needs Two Conditions</h2>
 * <p>
 * The {@code covers()} method uses {@code >=} semantics. When newToken equals tokenAtReset,
 * both {@code newToken.covers(tokenAtReset)} AND {@code tokenAtReset.covers(newToken)} are true.
 * <p>
 * Using only {@code newToken.covers(tokenAtReset)} would incorrectly exit replay at the boundary.
 * The second condition {@code !tokenAtReset.covers(newToken)} ensures we only exit when
 * we're STRICTLY PAST the reset point.
 *
 * <h2>Example Timeline (GlobalSequenceTrackingToken)</h2>
 * <pre>
 * Events:    [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
 *             ←─── SEEN BEFORE RESET ───→│←─ NEW ─→
 *                                        │
 *                               tokenAtReset=7
 *
 * Event 5: tokenAtReset.covers(5)=TRUE              → Case 2 (replay)
 * Event 7: covers(7)=TRUE, !covers(7)=FALSE         → Case 2 (replay) ← boundary!
 * Event 8: covers(7)=TRUE, !covers(8)=TRUE          → Case 1 (exit replay)
 * </pre>
 *
 * @author Allard Buijze
 * @since 3.2
 * @see TrackingToken#covers(TrackingToken)
 * @see GapAwareTrackingToken
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
        // ─────────────────────────────────────────────────────────────────────────────
        // STEP 1: Handle null tokenAtReset
        // ─────────────────────────────────────────────────────────────────────────────
        // If there's no token at reset, we have nothing to replay against.
        // Just return the startPosition directly (no ReplayToken wrapper needed).
        // Example: First-time processor startup with no prior state.
        if (tokenAtReset == null) {
            return startPosition;
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // STEP 2: Handle nested ReplayToken (double reset scenario)
        // ─────────────────────────────────────────────────────────────────────────────
        // If someone triggers a reset while already in replay mode, we don't want
        // nested ReplayTokens. Instead, unwrap to the original tokenAtReset.
        // This ensures we always track against the original "high water mark".
        if (tokenAtReset instanceof ReplayToken) {
            return createReplayToken(((ReplayToken) tokenAtReset).tokenAtReset, startPosition, resetContext);
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // STEP 3: Check if startPosition already covers tokenAtReset
        // ─────────────────────────────────────────────────────────────────────────────
        // If the startPosition has already seen everything that tokenAtReset saw,
        // there's nothing to replay! Just return startPosition directly.
        // Example: tokenAtReset=5, startPosition=10 → startPosition.covers(5)=true
        //          No replay needed since we're starting ahead of the reset point.
        if (startPosition != null && startPosition.covers(WrappedToken.unwrapLowerBound(tokenAtReset))) {
            return startPosition;
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // STEP 4: Create the ReplayToken
        // ─────────────────────────────────────────────────────────────────────────────
        // Normal case: We need to replay from startPosition until we catch up to tokenAtReset.
        // Example: tokenAtReset=10, startPosition=null (or 0)
        //          → Events 0-10 will be marked as replays, event 11+ will be new.
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

    /**
     * Advances this ReplayToken to a new position, determining whether the event at {@code newToken}
     * is a replay (previously seen) or a new event.
     * <p>
     * This method implements a three-case decision tree:
     * <ul>
     *   <li><b>Case 1 (Done Replaying)</b>: The newToken has advanced strictly past tokenAtReset</li>
     *   <li><b>Case 2 (Still Replaying)</b>: The newToken represents an event that was seen before reset</li>
     *   <li><b>Case 3 (Partial Catch-up)</b>: The newToken is a NEW event, but we haven't fully caught up yet
     *       (happens with gaps in GapAwareTrackingToken)</li>
     * </ul>
     *
     * <h3>Understanding the Case 1 Condition</h3>
     * <pre>
     * newToken.covers(tokenAtReset)           // Condition A: "Have we reached/passed the reset point?"
     * && !tokenAtReset.covers(newToken)       // Condition B: "Is this event actually NEW?"
     * </pre>
     *
     * <b>Why do we need BOTH conditions?</b>
     * <p>
     * The {@code covers()} method uses {@code >=} semantics, not {@code >}. When two tokens are at the
     * SAME position, they mutually cover each other. Without Condition B, we would prematurely exit
     * replay when processing the exact event at the reset boundary.
     * <p>
     * Example with GlobalSequenceTrackingToken:
     * <pre>
     * tokenAtReset = 7, newToken = 7
     *
     * Condition A alone: newToken.covers(tokenAtReset) = (7 >= 7) = TRUE
     * → Would incorrectly exit replay! But event 7 WAS seen before reset.
     *
     * With both conditions:
     * Condition A: newToken.covers(tokenAtReset) = TRUE
     * Condition B: !tokenAtReset.covers(newToken) = !(7 >= 7) = FALSE
     * Combined: TRUE && FALSE = FALSE → Stay in replay mode ✓
     * </pre>
     *
     * <h3>Visual Timeline</h3>
     * <pre>
     * Event Stream:    [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
     *                   ←─── SEEN BEFORE RESET ───→│←─ NEW ─→
     *                                              │
     *                                     tokenAtReset=7
     *
     * Event 7: covers(7)=YES, !covers(7)=NO  → Combined=FALSE → Still replay (Case 2)
     * Event 8: covers(7)=YES, !covers(8)=YES → Combined=TRUE  → Exit replay (Case 1)
     * </pre>
     *
     * @param newToken The token representing the position of the new event being processed
     * @return A token representing the new state after processing the event
     */
    @Override
    public TrackingToken advancedTo(TrackingToken newToken) {
        // ═══════════════════════════════════════════════════════════════════════════════
        // CASE 1: DONE REPLAYING
        // ═══════════════════════════════════════════════════════════════════════════════
        // We exit replay mode when BOTH conditions are true:
        //   Condition A: newToken.covers(tokenAtReset) - "We've reached or passed the reset point"
        //   Condition B: !tokenAtReset.covers(newToken) - "This specific event is NEW (not seen before)"
        //
        // WHY BOTH CONDITIONS?
        // - covers() uses >= semantics, so tokens at the SAME position mutually cover each other
        // - Condition A alone would incorrectly exit replay at the boundary (e.g., both at position 7)
        // - Condition B ensures we only exit when we're STRICTLY PAST the reset point
        //
        // Example: tokenAtReset=7
        //   newToken=7: covers(7)=TRUE, !covers(7)=FALSE → FALSE → Stay in replay
        //   newToken=8: covers(7)=TRUE, !covers(8)=TRUE  → TRUE  → Exit replay
        if (this.tokenAtReset == null || isStrictlyAfterTokenAtReset(newToken)) {
            // We're done replaying - return the unwrapped newToken
            // If tokenAtReset was a WrappedToken, delegate to it to maintain any wrapper state
            if (tokenAtReset instanceof WrappedToken) {
                return ((WrappedToken) tokenAtReset).advancedTo(newToken);
            }
            return newToken;
        }

        // ═══════════════════════════════════════════════════════════════════════════════
        // CASE 2: STILL REPLAYING
        // ═══════════════════════════════════════════════════════════════════════════════
        // The event at newToken was seen before reset → it's definitely a replay.
        // tokenAtReset.covers(newToken) means the reset point "covers" this position,
        // i.e., this event was already processed before the reset occurred.
        //
        // Example: tokenAtReset=7, newToken=5
        //   tokenAtReset.covers(5) = (7 >= 5) = TRUE → This is a replay
        else if (isBeforeOrEqualTokenAtReset(newToken)) {
            // Create a new ReplayToken with lastMessageWasReplay=true
            return new ReplayToken(
                    tokenAtReset, // we don't do upperBound here, because it's just useful if the lastMessageWasReplay. We pretend in the code we don't know about gaps, but looks like we know
                    newToken,
                    context,
                    true
            );
        }

        // ═══════════════════════════════════════════════════════════════════════════════
        // CASE 3: PARTIAL CATCH-UP (Edge case with gaps)
        // ═══════════════════════════════════════════════════════════════════════════════
        // We reach here when:
        //   - newToken does NOT cover tokenAtReset (we haven't fully caught up)
        //   - tokenAtReset does NOT cover newToken (this event wasn't seen before)
        //
        // This happens with GapAwareTrackingToken when we encounter a GAP event:
        //
        // Example: tokenAtReset = GapAwareTrackingToken(index=10, gaps={7})
        //          This means events 0-6, 8-10 were seen, but event 7 was NOT.
        //
        //          newToken = GapAwareTrackingToken(index=7, gaps={})
        //          We're processing event 7 (the gap).
        //
        //          Check Case 1: newToken.covers(tokenAtReset) = (7).covers(10) = FALSE
        //          Check Case 2: tokenAtReset.covers(newToken) = (10,{7}).covers(7) = FALSE
        //                        (returns FALSE because 7 is IN the gaps - it wasn't seen!)
        //
        //          → Event 7 is NEW (not a replay), but we haven't finished replay yet
        //          → lastMessageWasReplay = false (this specific event is new)
        //          → Update tokenAtReset to include this newly seen event via upperBound()
        else {
            // This event is NEW (wasn't seen before), but replay isn't finished yet.
            // Merge the new event into tokenAtReset using upperBound() to track progress.
            if (tokenAtReset instanceof WrappedToken) {
                return new ReplayToken(tokenAtReset.upperBound(newToken),
                                       ((WrappedToken) tokenAtReset).advancedTo(newToken),
                                       context,
                                       false);  // false = this message was NOT a replay
            }
            return new ReplayToken(tokenAtReset.upperBound(newToken), newToken, context, false);
        }
    }

    private boolean isStrictlyAfterTokenAtReset(TrackingToken newToken) {
        return isAfterOrEqualTokenAtReset(newToken)
                && !isBeforeOrEqualTokenAtReset(newToken);
    }

    private boolean isAfterOrEqualTokenAtReset(TrackingToken newToken) {
        return newToken.covers(WrappedToken.unwrapUpperBound(this.tokenAtReset));
    }

    // tokenAtReset >= newToken
    // newToken <= tokenAtReset
    private boolean isBeforeOrEqualTokenAtReset(TrackingToken newToken) {
        return tokenAtReset.covers(WrappedToken.unwrapLowerBound(newToken));
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

    /**
     * Determines if this ReplayToken has seen all events that the {@code other} token has seen.
     * <p>
     * This method delegates to the {@code currentToken}'s covers() method, because during replay
     * the "progress" is tracked by currentToken, not tokenAtReset.
     * <p>
     * <b>Important:</b> The covers() method is the decision-maker in {@link #advancedTo(TrackingToken)}.
     * Understanding covers() is essential for understanding replay detection:
     * <ul>
     *   <li>For {@code GlobalSequenceTrackingToken}: covers(other) = (this.index >= other.index)</li>
     *   <li>For {@code GapAwareTrackingToken}: covers(other) = (other.index <= this.index)
     *       AND (other.index not in this.gaps) AND (this.gaps subset of other.gaps)</li>
     * </ul>
     *
     * @param other The token to compare against
     * @return {@code true} if this token covers the other token
     */
    @Override
    public boolean covers(TrackingToken other) {
        // For ReplayToken, we compare currentToken (our progress) against the other's progress.
        // The tokenAtReset is not relevant here - it's only used in advancedTo() to determine
        // if an event is a replay or not.
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
