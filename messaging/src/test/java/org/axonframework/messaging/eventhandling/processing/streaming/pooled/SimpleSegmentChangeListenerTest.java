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

package org.axonframework.messaging.eventhandling.processing.streaming.pooled;

import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SegmentChangeListener;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SimpleSegmentChangeListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.common.FutureUtils.emptyCompletedFuture;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleSegmentChangeListenerTest {

    @Test
    void runOnReleaseInvokesReleaseOnly() {
        AtomicInteger releaseInvocations = new AtomicInteger();
        SegmentChangeListener testSubject = SegmentChangeListener.runOnRelease(segment -> {
            releaseInvocations.incrementAndGet();
        });

        joinAndUnwrap(testSubject.onSegmentClaimed(Segment.ROOT_SEGMENT));
        joinAndUnwrap(testSubject.onSegmentReleased(Segment.ROOT_SEGMENT));

        assertEquals(1, releaseInvocations.get());
    }

    @Test
    void onClaimWithAsyncFunctionReturnsFunctionResult() {
        CompletableFuture<Void> expected = new CompletableFuture<>();
        SegmentChangeListener testSubject = SegmentChangeListener.onClaim(segment -> expected);

        assertSame(expected, testSubject.onSegmentClaimed(Segment.ROOT_SEGMENT));
        joinAndUnwrap(testSubject.onSegmentReleased(Segment.ROOT_SEGMENT));
    }

    @Test
    void simpleSegmentChangeListenerInvokesBothHandlers() {
        AtomicInteger claimInvocations = new AtomicInteger();
        AtomicInteger releaseInvocations = new AtomicInteger();
        SegmentChangeListener testSubject = new SimpleSegmentChangeListener(
                segment -> {
                    claimInvocations.incrementAndGet();
                    return emptyCompletedFuture();
                },
                segment -> {
                    releaseInvocations.incrementAndGet();
                    return emptyCompletedFuture();
                }
        );

        joinAndUnwrap(testSubject.onSegmentClaimed(Segment.ROOT_SEGMENT));
        joinAndUnwrap(testSubject.onSegmentReleased(Segment.ROOT_SEGMENT));

        assertEquals(1, claimInvocations.get());
        assertEquals(1, releaseInvocations.get());
    }

    @Test
    void andThenComposesListenersSequentially() {
        CompletableFuture<Void> firstClaim = new CompletableFuture<>();
        CompletableFuture<Void> firstRelease = new CompletableFuture<>();
        AtomicBoolean secondClaimInvoked = new AtomicBoolean();
        AtomicBoolean secondReleaseInvoked = new AtomicBoolean();

        SegmentChangeListener firstListener = new SimpleSegmentChangeListener(
                segment -> firstClaim,
                segment -> firstRelease
        );
        SegmentChangeListener secondListener = new SimpleSegmentChangeListener(
                segment -> {
                    secondClaimInvoked.set(true);
                    return emptyCompletedFuture();
                },
                segment -> {
                    secondReleaseInvoked.set(true);
                    return emptyCompletedFuture();
                }
        );
        SegmentChangeListener testSubject = firstListener.andThen(secondListener);

        CompletableFuture<Void> claimHandle = testSubject.onSegmentClaimed(Segment.ROOT_SEGMENT);
        assertFalse(secondClaimInvoked.get());
        firstClaim.complete(null);
        joinAndUnwrap(claimHandle);
        assertTrue(secondClaimInvoked.get());

        CompletableFuture<Void> releaseHandle = testSubject.onSegmentReleased(Segment.ROOT_SEGMENT);
        assertFalse(secondReleaseInvoked.get());
        firstRelease.complete(null);
        joinAndUnwrap(releaseHandle);
        assertTrue(secondReleaseInvoked.get());
    }
}
