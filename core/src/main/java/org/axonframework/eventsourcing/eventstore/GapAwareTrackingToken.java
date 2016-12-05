/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.Assert;

import java.util.*;
import java.util.stream.LongStream;

/**
 * Implementation of a {@link TrackingToken} that uses the global insertion sequence number of the event to determine
 * tracking order and additionally stores a set of possible gaps that have been detected while tracking the event
 * store.
 * <p>
 * By storing the sequence numbers of gaps, i.e. sequence numbers of events that may have been inserted but have not
 * been committed to the store, consumers are able to track the event store uninterruptedly even when there are gaps
 * in the sequence numbers of events. If a gap is detected the event store can check if meanwhile this gap has been
 * filled each time a new batch of events is fetched.
 *
 * @author Rene de Waele
 */
public class GapAwareTrackingToken implements TrackingToken {

    private final long index;
    private final SortedSet<Long> gaps;

    /**
     * Returns a new {@link GapAwareTrackingToken} instance based on the given {@code index} and collection of {@code
     * gaps}.
     *
     * @param index the highest global sequence number of events up until (and including) this tracking token
     * @param gaps  global sequence numbers of events that have not been seen yet even though these sequence numbers are
     *              smaller than the current index. These missing sequence numbers may be filled in later when those
     *              events get committed to the store or may never be filled in if those events never get committed.
     * @return a new tracking token from given index and gaps
     */
    public static GapAwareTrackingToken newInstance(long index, Collection<Long> gaps) {
        if (gaps.isEmpty()) {
            return new GapAwareTrackingToken(index, Collections.emptySortedSet());
        }
        SortedSet<Long> gapSet = new TreeSet<>(gaps);
        Assert.isTrue(gapSet.last() < index,
                      () -> String.format("Gap indices [%s] should all be smaller than head index [%d]", gaps, index));
        return new GapAwareTrackingToken(index, gapSet);
    }

    private GapAwareTrackingToken(long index, SortedSet<Long> gaps) {
        this.index = index;
        this.gaps = gaps;
    }

    /**
     * Returns a new {@link GapAwareTrackingToken} instance based on this token but which has advanced to given {@code
     * index}. Gaps that have fallen behind the index by more than the {@code maxGapOffset} will not be included in the
     * new token.
     *
     * Note that the given {@code index} should be one of the current token's gaps or be higher than the current token's
     * index.
     *
     * @param index the global sequence number of the next event
     * @param maxGapOffset the maximum distance between a gap and the token's index
     * @return the new token that has advanced from the current token
     */
    public GapAwareTrackingToken advanceTo(long index, int maxGapOffset) {
        long newIndex;
        SortedSet<Long> gaps = new TreeSet<>(this.gaps);
        if (gaps.remove(index)) {
            newIndex = this.index;
        } else if (index > this.index) {
            newIndex = index;
            LongStream.range(this.index + 1L, index).forEach(gaps::add);
        } else {
            throw new IllegalArgumentException(String.format(
                    "The given index [%d] should be larger than the token index [%d] or be one of the token's gaps [%s]",
                    index, this.index, gaps));
        }
        gaps = gaps.tailSet(newIndex - maxGapOffset);
        return new GapAwareTrackingToken(newIndex, gaps);
    }

    /**
     * Get the highest global sequence of events seen up until the point of this tracking token.
     *
     * @return the highest global event sequence number seen so far
     */
    public long getIndex() {
        return index;
    }

    /**
     * Get a {@link SortedSet} of this token's gaps.
     *
     * @return the gaps of this token
     */
    public SortedSet<Long> getGaps() {
        return Collections.unmodifiableSortedSet(gaps);
    }

    /**
     * Check if this token contains one ore more gaps.
     *
     * @return {@code true} if this token contains gaps, {@code false} otherwise
     */
    public boolean hasGaps() {
        return !gaps.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GapAwareTrackingToken that = (GapAwareTrackingToken) o;
        return index == that.index && Objects.equals(gaps, that.gaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, gaps);
    }

    @Override
    public String toString() {
        return "GapAwareTrackingToken{" + "index=" + index + ", gaps=" + gaps + '}';
    }
}
