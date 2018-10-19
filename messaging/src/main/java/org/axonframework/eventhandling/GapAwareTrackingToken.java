/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.common.Assert;
import org.axonframework.common.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
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
public class GapAwareTrackingToken implements TrackingToken, Serializable {

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
    @JsonCreator
    public static GapAwareTrackingToken newInstance(@JsonProperty("index") long index,
                                                    @JsonProperty("gaps") Collection<Long> gaps) {
        if (gaps.isEmpty()) {
            return new GapAwareTrackingToken(index, Collections.emptySortedSet());
        }
        SortedSet<Long> gapSet = new ConcurrentSkipListSet<>(gaps);
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
     * <p>
     * Note that the given {@code index} should be one of the current token's gaps or be higher than the current token's
     * index.
     * <p>
     * If {@code allowGaps} is set to {@code false}, any gaps that occur before the given {@code index} are removed
     * from the returned token.
     *
     * @param index        the global sequence number of the next event
     * @param maxGapOffset the maximum distance between a gap and the token's index
     * @param allowGaps    whether advancing to the given index should take into account that gaps may have appeared
     * @return the new token that has advanced from the current token
     */
    public GapAwareTrackingToken advanceTo(long index, int maxGapOffset, boolean allowGaps) {
        long newIndex;
        SortedSet<Long> gaps = new ConcurrentSkipListSet<>(this.gaps);
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
        long smalledAllowedGap = allowGaps ? (newIndex - maxGapOffset) : Math.max(index, newIndex - maxGapOffset);
        gaps.removeAll(gaps.headSet(smalledAllowedGap));
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

    @Override
    public GapAwareTrackingToken lowerBound(TrackingToken other) {
        Assert.isTrue(other instanceof GapAwareTrackingToken, () -> "Incompatible token type provided.");
        GapAwareTrackingToken otherToken = (GapAwareTrackingToken) other;

        SortedSet<Long> mergedGaps = new ConcurrentSkipListSet<>(this.gaps);
        mergedGaps.addAll(otherToken.gaps);
        long mergedIndex = calculateIndex(otherToken, mergedGaps);
        mergedGaps.removeIf(i -> i >= mergedIndex);
        return new GapAwareTrackingToken(mergedIndex, mergedGaps);
    }

    @Override
    public TrackingToken upperBound(TrackingToken otherToken) {
        Assert.isTrue(otherToken instanceof GapAwareTrackingToken, () -> "Incompatible token type provided.");
        GapAwareTrackingToken other = (GapAwareTrackingToken) otherToken;
        SortedSet<Long> newGaps = CollectionUtils.intersect(this.gaps, other.gaps, ConcurrentSkipListSet::new);
        long min = Math.min(this.index, other.index) + 1;
        SortedSet<Long> mergedGaps = CollectionUtils.merge(this.gaps.tailSet(min), other.gaps.tailSet(min), ConcurrentSkipListSet::new);
        newGaps.addAll(mergedGaps);

        return new GapAwareTrackingToken(Math.max(this.index, other.index), newGaps);
    }

    private long calculateIndex(GapAwareTrackingToken otherToken, SortedSet<Long> mergedGaps) {
        long mergedIndex = Math.min(this.index, otherToken.index);
        while (mergedGaps.contains(mergedIndex)) {
            mergedIndex--;
        }
        return mergedIndex;
    }

    @Override
    public boolean covers(TrackingToken other) {
        Assert.isTrue(other instanceof GapAwareTrackingToken, () -> "Incompatible token type provided.");
        GapAwareTrackingToken otherToken = (GapAwareTrackingToken) other;

        return otherToken.index <= this.index
                && !this.gaps.contains(otherToken.index)
                && otherToken.gaps.containsAll(this.gaps.headSet(otherToken.index));
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
