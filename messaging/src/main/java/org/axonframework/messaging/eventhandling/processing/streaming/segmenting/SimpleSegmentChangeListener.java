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
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Simple {@link SegmentChangeListener} implementation backed by claim and release handlers.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public class SimpleSegmentChangeListener implements SegmentChangeListener {

    private final Function<Segment, CompletableFuture<Void>> onClaim;
    private final Function<Segment, CompletableFuture<Void>> onRelease;

    /**
     * Creates a listener with explicit claim and release handlers.
     *
     * @param onClaim   The claim handler.
     * @param onRelease The release handler.
     */
    public SimpleSegmentChangeListener(
            @Nonnull Function<Segment, CompletableFuture<Void>> onClaim,
            @Nonnull Function<Segment, CompletableFuture<Void>> onRelease
    ) {
        Objects.requireNonNull(onClaim, "Claim listener may not be null");
        Objects.requireNonNull(onRelease, "Release listener may not be null");
        this.onClaim = onClaim;
        this.onRelease = onRelease;
    }

    @Override
    public @Nonnull CompletableFuture<Void> onSegmentClaimed(@NonNull Segment segment) {
        return onClaim.apply(segment);
    }

    @Override
    public @NonNull CompletableFuture<Void> onSegmentReleased(@Nonnull Segment segment) {
        return onRelease.apply(segment);
    }
}
