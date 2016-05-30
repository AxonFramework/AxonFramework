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

/**
 * @author Rene de Waele
 */
public class GlobalIndexTrackingToken implements TrackingToken {

    private final long globalIndex;

    public GlobalIndexTrackingToken(long globalIndex) {
        this.globalIndex = globalIndex;
    }

    @Override
    public boolean isGuaranteedNext(TrackingToken otherToken) {
        return ((GlobalIndexTrackingToken) otherToken).globalIndex - globalIndex == 1;
    }

    public long getGlobalIndex() {
        return globalIndex;
    }

    public GlobalIndexTrackingToken offsetBy(int offset) {
        return new GlobalIndexTrackingToken(globalIndex + offset);
    }

    public GlobalIndexTrackingToken next() {
        return new GlobalIndexTrackingToken(globalIndex + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GlobalIndexTrackingToken that = (GlobalIndexTrackingToken) o;
        return globalIndex == that.globalIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalIndex);
    }

    @Override
    public int compareTo(TrackingToken o) {
        return Long.compare(globalIndex, ((GlobalIndexTrackingToken) o).globalIndex);
    }

    @Override
    public String toString() {
        return "IndexTrackingToken{" +
                "globalIndex=" + globalIndex +
                '}';
    }
}
