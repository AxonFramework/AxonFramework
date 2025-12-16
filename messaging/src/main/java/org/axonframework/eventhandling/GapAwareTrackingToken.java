/*
 * Copyright (c) 2010-2020. Axon Framework
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

import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.SortedSet;
import java.util.TreeSet;
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

    private static final long serialVersionUID = -3190388158060110593L;

    private final long index;
    private final SortedSet<Long> gaps;
    private final transient long gapTruncationIndex;

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
        return new GapAwareTrackingToken(index, gaps);
    }

    /**
     * This constructor is mean't to be used for deserialization. <br>
     * Please use {@link #newInstance(long, Collection)} to create new instances.
     *
     * @param index the highest global sequence number of events up until (and including) this tracking token
     * @param gaps  global sequence numbers of events that have not been seen yet even though these sequence numbers are
     *              smaller than the current index. These missing sequence numbers may be filled in later when those
     *              events get committed to the store or may never be filled in if those events never get committed.
     */
    @JsonCreator
    @ConstructorProperties({"index", "gaps"})
    public GapAwareTrackingToken(@JsonProperty("index") long index, @JsonProperty("gaps") Collection<Long> gaps) {
        this(index, createSortedSetOf(gaps, index), 0);
    }

    private GapAwareTrackingToken(long index, SortedSet<Long> gaps, long gapTruncationIndex) {
        this.index = index;
        this.gaps = gaps;
        this.gapTruncationIndex = gapTruncationIndex;
    }

    /**
     * Construct a {@link SortedSet} of the given {@code gaps} to be set in this Tracking Token. The given {@code index}
     * will be consolidated to ensure the last gap in the set is smaller. If this is not the case, an
     * {@link IllegalArgumentException} is thrown
     *
     * @param gaps  the {@link Collection} of gaps to created a {@link SortedSet} out of
     * @param index a {@code long} which is required to be bigger than the last known gap in the set
     * @return a {@link SortedSet} constructed out of the given {@code gaps}
     */
    protected static SortedSet<Long> createSortedSetOf(Collection<Long> gaps, long index) {
        if (gaps == null || gaps.isEmpty()) {
            return Collections.emptySortedSet();
        }
        SortedSet<Long> gapSet = new TreeSet<>(gaps);
        Assert.isTrue(gapSet.last() < index,
                      () -> String.format("Gap indices [%s] should all be smaller than head index [%d]", gaps, index));
        return gapSet;
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
     * @return the new token that has advanced from the current token
     */
    public GapAwareTrackingToken advanceTo(long index, int maxGapOffset) {
        long newIndex;
        long smalledAllowedGap = Math.min(index, Math.max(gapTruncationIndex, Math.max(index, this.index) - maxGapOffset));
        SortedSet<Long> gaps = new TreeSet<>(this.gaps.tailSet(smalledAllowedGap));
        if (gaps.remove(index) || this.gaps.contains(index)) {
            newIndex = this.index;
        } else if (index > this.index) {
            newIndex = index;
            LongStream.range(Math.max(this.index + 1L, smalledAllowedGap), index).forEach(gaps::add);
        } else {
            throw new IllegalArgumentException(String.format(
                    "The given index [%d] should be larger than the token index [%d] or be one of the token's gaps [%s]",
                    index, this.index, gaps));
        }
        return new GapAwareTrackingToken(newIndex, gaps, smalledAllowedGap);
    }

    /**
     * Returns a copy of the current token, with gaps truncated at the given {@code truncationPoint}. This removes any
     * gaps with index strictly smaller than the {@code truncationPoint} and disregards these when comparing this token
     * to any other tokens.
     * <p>
     * Note that truncation information is not serialized as part of the token.
     *
     * @param truncationPoint The index up to (and including) which gaps are to be disregarded.
     * @return a Token without any gaps strictly smaller than given {@code truncationPoint}
     */
    public GapAwareTrackingToken withGapsTruncatedAt(long truncationPoint) {
        if (gaps.isEmpty() || gaps.first() > truncationPoint) {
            return this;
        }
        SortedSet<Long> truncatedGaps = new TreeSet<>(this.gaps.tailSet(truncationPoint));
        return new GapAwareTrackingToken(this.index, truncatedGaps, truncationPoint);
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

        SortedSet<Long> mergedGaps = new TreeSet<>(this.gaps);
        mergedGaps.addAll(otherToken.gaps);
        long mergedIndex = calculateIndex(otherToken, mergedGaps);
        mergedGaps.removeIf(i -> i >= mergedIndex);
        return new GapAwareTrackingToken(mergedIndex, mergedGaps, Math.min(gapTruncationIndex,
                                                                           otherToken.gapTruncationIndex));
    }

    @Override
    public TrackingToken upperBound(TrackingToken otherToken) {
        Assert.isTrue(otherToken instanceof GapAwareTrackingToken, () -> "Incompatible token type provided.");
        GapAwareTrackingToken other = (GapAwareTrackingToken) otherToken;
        SortedSet<Long> newGaps = CollectionUtils.intersect(this.gaps, other.gaps, TreeSet::new);
        long min = Math.min(this.index, other.index) + 1;
        SortedSet<Long> mergedGaps =
                CollectionUtils.merge(this.gaps.tailSet(min), other.gaps.tailSet(min), TreeSet::new);
        newGaps.addAll(mergedGaps);

        return new GapAwareTrackingToken(Math.max(this.index, other.index), newGaps,
                                         Math.min(gapTruncationIndex, other.gapTruncationIndex));
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

        // if the token we compare to has a higher gap truncation index, we need to truncate this instance to compare
        if (!this.gaps.isEmpty()
                && !this.gaps.headSet(otherToken.gapTruncationIndex).isEmpty()
                && this.gapTruncationIndex < otherToken.gapTruncationIndex) {
            return this.withGapsTruncatedAt(otherToken.gapTruncationIndex).covers(other);
        }

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
        long truncationIndex = Math.max(this.gapTruncationIndex, that.gapTruncationIndex) + 1;
        return index == that.index && Objects.equals(gaps.tailSet(truncationIndex), that.gaps.tailSet(truncationIndex));
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    @Override
    public String toString() {
        return "GapAwareTrackingToken{" + "index=" + index + ", gaps=" + gaps + '}';
    }

    @Override
    public OptionalLong position() {
        return OptionalLong.of(index);
    }
}
