/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * This will contain a representation of the sequence identifiers belonging to a single segment. This way we know from
 * memory which identifier is, or is not already present. This in turn reduces the amount of calls directly to a
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue} implementation.
 *
 * @author Gerard Klijs
 * @since 4.9.0
 */
class DeadLetteringCacheEntry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final int segmentId;
    private final boolean startedEmpty;
    private final int maxSize;
    private final LinkedList<Object> identifiersNotInDLQ = new LinkedList<>();
    private final Set<Object> identifiersInDLQ = new HashSet<>();

    DeadLetteringCacheEntry(int segmentId, int maxSize, SequencedDeadLetterQueue<EventMessage<?>> queue) {
        this.segmentId = segmentId;
        this.maxSize = maxSize;
        this.startedEmpty = queue.amountOfSequences() == 0L;
    }

    boolean skipIfPresentCheck(Object sequenceIdentifier) {
        if (identifiersInDLQ.contains(sequenceIdentifier)) {
            return false;
        }
        return startedEmpty || identifiersNotInDLQ.contains(sequenceIdentifier);
    }

    DeadLetteringCacheEntry markPresentInDLQ(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as present to the cache for segment [{}].",
                         sequenceIdentifier,
                         segmentId);
        }
        identifiersInDLQ.add(sequenceIdentifier);
        identifiersNotInDLQ.remove(sequenceIdentifier);
        return this;
    }

    DeadLetteringCacheEntry markNotPresentInDLQ(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as not present to the cache for segment [{}].",
                         sequenceIdentifier,
                         segmentId);
        }
        if (!startedEmpty) {
            identifiersNotInDLQ.add(sequenceIdentifier);
            if (identifiersNotInDLQ.size() > maxSize) {
                identifiersNotInDLQ.removeFirst();
            }
        }
        identifiersInDLQ.remove(sequenceIdentifier);
        return this;
    }
}
