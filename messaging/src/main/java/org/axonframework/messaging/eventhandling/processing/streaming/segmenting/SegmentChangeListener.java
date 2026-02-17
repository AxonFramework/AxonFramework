/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.segmenting;

import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Listener invoked when a processor claims or releases a {@link Segment}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public interface SegmentChangeListener {

    /**
     * Creates a listener that reacts only to claim events.
     *
     * @param onClaim asynchronous claim callback
     * @return listener reacting to claim events
     */
    @Nonnull
    static SegmentChangeListener onClaim(@Nonnull Function<Segment, CompletableFuture<Void>> onClaim) {
        return new SimpleSegmentChangeListener(onClaim, segment -> CompletableFuture.completedFuture(null));
    }

    /**
     * Creates a listener that reacts only to release events.
     *
     * @param onRelease asynchronous release callback
     * @return listener reacting to release events
     */
    @Nonnull
    static SegmentChangeListener onRelease(@Nonnull Function<Segment, CompletableFuture<Void>> onRelease) {
        return new SimpleSegmentChangeListener(segment -> CompletableFuture.completedFuture(null), onRelease);
    }

    /**
     * Creates a listener that executes synchronously on claim events.
     *
     * @param onClaim synchronous claim callback
     * @return listener reacting to claim events
     */
    @Nonnull
    static SegmentChangeListener runOnClaim(@Nonnull Consumer<Segment> onClaim) {
        Objects.requireNonNull(onClaim, "Claim listener may not be null");
        return new SimpleSegmentChangeListener(segment -> {
            onClaim.accept(segment);
            return CompletableFuture.completedFuture(null);
        }, segment -> CompletableFuture.completedFuture(null));
    }

    /**
     * Creates a listener that executes synchronously on release events.
     *
     * @param onRelease synchronous release callback
     * @return listener reacting to release events
     */
    @Nonnull
    static SegmentChangeListener runOnRelease(@Nonnull Consumer<Segment> onRelease) {
        Objects.requireNonNull(onRelease, "Release listener may not be null");
        return new SimpleSegmentChangeListener(segment -> CompletableFuture.completedFuture(null), segment -> {
            onRelease.accept(segment);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Returns a no-op listener.
     *
     * @return no-op segment change listener
     */
    @Nonnull
    static SegmentChangeListener noOp() {
        return new SimpleSegmentChangeListener(
                segment -> CompletableFuture.completedFuture(null),
                segment -> CompletableFuture.completedFuture(null)
        );
    }

    /**
     * Invoked when a segment has been claimed and processing for that segment is started.
     *
     * @param segment claimed {@link Segment}
     * @return {@link CompletableFuture} that completes when handling has finished
     */
    @Nonnull
    CompletableFuture<Void> onSegmentClaimed(@Nonnull Segment segment);

    /**
     * Invoked when a segment has been released.
     *
     * @param segment released {@link Segment}
     * @return {@link CompletableFuture} that completes when handling has finished
     */
    @Nonnull
    CompletableFuture<Void> onSegmentReleased(@Nonnull Segment segment);

    /**
     * Compose this listener with {@code next}, invoking this listener first and the next listener second.
     *
     * @param next listener to invoke after this listener
     * @return composed listener invoking listeners sequentially for claim and release events
     */
    @Nonnull
    default SegmentChangeListener andThen(@Nonnull SegmentChangeListener next) {
        Objects.requireNonNull(next, "Next listener may not be null");
        return new SimpleSegmentChangeListener(
                segment -> onSegmentClaimed(segment).thenCompose(unused -> next.onSegmentClaimed(segment)),
                segment -> onSegmentReleased(segment).thenCompose(unused -> next.onSegmentReleased(segment))
        );
    }
}
