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
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue} implementation. In case the {@code queue} is
 * not empty, we need to keep track of the identifiers not present as well.
 *
 * @author Gerard Klijs
 * @since 4.9.0
 */
class SequenceIdentifierCache {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final int segmentId;
    private final boolean startedEmpty;
    private final int maxSize;
    private final LinkedList<Object> nonEnqueuedIdentifiers = new LinkedList<>();
    private final Set<Object> enqueuedIdentifiers = new HashSet<>();

    /**
     * The constructor of the {@link SequenceIdentifierCache}, which typically will be called if either the
     * {@link DeadLetteringEventHandlerInvoker} has not processed the {@link org.axonframework.eventhandling.Segment}
     * before, or the segment was released in the meantime.
     *
     * @param segmentId the id of the segment, used only for logging purposes.
     * @param maxSize   the maximum size of the {@link #nonEnqueuedIdentifiers}, this prevents memory leak in case the
     *                  queue is not empty.
     * @param queue     the {@link SequencedDeadLetterQueue} this cache is used for. If it's empty we can optimize by
     *                  only keeping track of the sequence identifiers which are enqueued. If it's not empty we need to
     *                  check every identifier at least one to know it's not present yet.
     */
    SequenceIdentifierCache(int segmentId, int maxSize, SequencedDeadLetterQueue<EventMessage<?>> queue) {
        this.segmentId = segmentId;
        this.maxSize = maxSize;
        this.startedEmpty = queue.amountOfSequences() == 0L;
    }

    boolean mightBePresent(Object sequenceIdentifier) {
        if (enqueuedIdentifiers.contains(sequenceIdentifier)) {
            return true;
        }
        if (startedEmpty) {
            return false;
        }
        return !nonEnqueuedIdentifiers.contains(sequenceIdentifier);
    }

    SequenceIdentifierCache markEnqueued(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as present to the cache for segment [{}].",
                         sequenceIdentifier,
                         segmentId);
        }
        enqueuedIdentifiers.add(sequenceIdentifier);
        nonEnqueuedIdentifiers.remove(sequenceIdentifier);
        return this;
    }

    SequenceIdentifierCache markNotEnqueued(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as not present to the cache for segment [{}].",
                         sequenceIdentifier,
                         segmentId);
        }
        if (!startedEmpty) {
            nonEnqueuedIdentifiers.add(sequenceIdentifier);
            if (nonEnqueuedIdentifiers.size() > maxSize) {
                nonEnqueuedIdentifiers.removeFirst();
            }
        }
        enqueuedIdentifiers.remove(sequenceIdentifier);
        return this;
    }
}
