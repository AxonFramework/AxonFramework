/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException.conflictingEventsDetected;

public class LegacyAggregateBasedEventStorageEngineUtils {

    public static void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new TooManyTagsOnEventMessageException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event",
                        taggedEvent.event(),
                        taggedEvent.tags());
            }
        }
    }

    @Nullable
    public static String resolveAggregateIdentifier(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().value();
        }
    }

    @Nullable
    public static String resolveAggregateType(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().key();
        }
    }

    public static Throwable translateConflictException(
            ConsistencyMarker consistencyMarker,
            Throwable e,
            Predicate<Throwable> isConflictException
    ) {
        if (isConflictException.test(e)) {
            AppendEventsTransactionRejectedException translated = conflictingEventsDetected(consistencyMarker);
            translated.addSuppressed(e);
            return translated;
        }
        if (e.getCause() != null) {
            Throwable translatedCause = translateConflictException(consistencyMarker,
                                                                   e.getCause(),
                                                                   isConflictException);
            if (translatedCause != e.getCause()) {
                return translatedCause;
            }
        }
        return e;
    }

    public static final class AggregateSequencer {

        private final Map<String, AtomicLong> aggregateSequences;
        private AggregateBasedConsistencyMarker consistencyMarker;

        AggregateSequencer(Map<String, AtomicLong> aggregateSequences,
                           AggregateBasedConsistencyMarker consistencyMarker) {
            this.aggregateSequences = aggregateSequences;
            this.consistencyMarker = consistencyMarker;
        }

        public static AggregateSequencer with(AggregateBasedConsistencyMarker consistencyMarker) {
            return new AggregateSequencer(new HashMap<>(), consistencyMarker);
        }

        public AggregateBasedConsistencyMarker forwarded() {
            var newConsistencyMarker = consistencyMarker;
            for (var aggSeq : aggregateSequences.entrySet()) {
                newConsistencyMarker = newConsistencyMarker
                        .forwarded(aggSeq.getKey(), aggSeq.getValue().get());
            }
            consistencyMarker = newConsistencyMarker;
            return newConsistencyMarker;
        }

        public AtomicLong resolveBy(String aggregateIdentifier) {
            return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                      i -> new AtomicLong(consistencyMarker.positionOf(i)));
        }
    }
}
