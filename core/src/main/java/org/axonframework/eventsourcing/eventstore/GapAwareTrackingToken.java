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

import java.util.Objects;
import java.util.SortedSet;

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

    public GapAwareTrackingToken(long index, SortedSet<Long> gaps) {
        this.index = index;
        this.gaps = gaps;
    }

    @Override
    public boolean isGuaranteedNext(TrackingToken otherToken) {
        return ((GapAwareTrackingToken) otherToken).index - index == 1;
    }

    public long getIndex() {
        return index;
    }

    public GapAwareTrackingToken next() {
        return new GapAwareTrackingToken(index + 1, gaps);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GapAwareTrackingToken that = (GapAwareTrackingToken) o;
        return index == that.index &&
                Objects.equals(gaps, that.gaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, gaps);
    }

    @Override
    public int compareTo(TrackingToken o) {
        return Long.compare(index, ((GapAwareTrackingToken) o).index);
    }

    @Override
    public String toString() {
        return "GapAwareTrackingToken{" +
                "index=" + index +
                ", gaps=" + gaps +
                '}';
    }
}
