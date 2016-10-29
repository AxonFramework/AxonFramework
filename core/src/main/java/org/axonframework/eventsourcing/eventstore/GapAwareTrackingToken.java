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

    private static final Comparator<GapAwareTrackingToken> COMPARATOR =
            Comparator.comparingLong(GapAwareTrackingToken::getIndex).thenComparing((t1, t2) -> {
                if (t1.gaps.equals(t2.gaps)) {
                    return 0;
                }
                int sizeDelta = t1.gaps.size() - t2.gaps.size();
                if (sizeDelta != 0) {
                    return -sizeDelta;
                }
                Iterator<Long> it1 = t1.gaps.iterator();
                Iterator<Long> it2 = t2.gaps.iterator();
                while (it1.hasNext()) {
                    int sign = Long.signum(it1.next() - it2.next());
                    if (sign != 0) {
                        return sign;
                    }
                }
                throw new AssertionError("This point should be unreachable");
            });

    private final long index;
    private final SortedSet<Long> gaps;

    public static GapAwareTrackingToken newInstance(long index, Collection<Long> gaps) {
        if (gaps.isEmpty()) {
            return new GapAwareTrackingToken(index, Collections.emptySortedSet());
        }
        SortedSet<Long> gapSet = new TreeSet<>(gaps);
        if (gapSet.last() >= index) {
            throw new IllegalArgumentException(
                    String.format("Gap indices [%s] should all be smaller than head index [%d]", gaps, index));
        }
        return new GapAwareTrackingToken(index, gapSet);
    }

    private GapAwareTrackingToken(long index, SortedSet<Long> gaps) {
        this.index = index;
        this.gaps = gaps;
    }

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
                    "The given index [%d] should be larger than the token index [%d] or one of the token's gaps [%s]",
                    index, this.index, gaps));
        }
        gaps = gaps.tailSet(newIndex - maxGapOffset);
        return new GapAwareTrackingToken(newIndex, gaps);
    }

    public long getIndex() {
        return index;
    }

    public SortedSet<Long> getGaps() {
        return Collections.unmodifiableSortedSet(gaps);
    }

    public boolean hasGaps() {
        return !gaps.isEmpty();
    }

    public GapAwareTrackingToken next() {
        return new GapAwareTrackingToken(index + 1, gaps);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        GapAwareTrackingToken that = (GapAwareTrackingToken) o;
        return index == that.index && Objects.equals(gaps, that.gaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, gaps);
    }

    @Override
    public int compareTo(TrackingToken o) {
        return COMPARATOR.compare(this, (GapAwareTrackingToken) o);
    }

    @Override
    public String toString() {
        return "GapAwareTrackingToken{" + "index=" + index + ", gaps=" + gaps + '}';
    }
}
