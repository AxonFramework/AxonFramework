/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * In-memory implementation of the {@link SequencedDeadLetterQueue}.
 * <p>
 * Maintains a {@link Deque} per unique {@link SequenceIdentifier}. The maximum amount of {@code Deques} contained by
 * this {@code SequencedDeadLetterQueue} is {@code 1024} (configurable through {@link Builder#maxSequences(int)}). The
 * maximum amount of {@link DeadLetter dead-letters} per sequence also defaults to {@code 1024} (configurable through
 * {@link Builder#maxSequenceSize(int)}).
 *
 * @param <D> The type of {@link DeadLetter dead-letter} maintained in this {@link SequencedDeadLetterQueue}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemorySequencedDeadLetterQueue<D extends DeadLetter<? extends Message<?>>> implements
        SequencedDeadLetterQueue<D> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<SequenceIdentifier, Deque<D>> deadLetters = new ConcurrentSkipListMap<>();
    private final Set<SequenceIdentifier> takenSequences = new ConcurrentSkipListSet<>();

    private final int maxSequences;
    private final int maxSequenceSize;

    /**
     * Instantiate an in-memory {@link SequencedDeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link InMemorySequencedDeadLetterQueue} instance.
     */
    protected InMemorySequencedDeadLetterQueue(Builder<D> builder) {
        builder.validate();
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
    }

    /**
     * Instantiate a builder to construct an {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead-letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <D> The type of {@link DeadLetter} maintained in this {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <D extends DeadLetter<? extends Message<?>>> Builder<D> builder() {
        return new Builder<>();
    }

    /**
     * Construct a default {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead-letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <D> The type of {@link DeadLetter} maintained in this {@link SequencedDeadLetterQueue}.
     * @return A default {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <D extends DeadLetter<? extends Message<?>>> InMemorySequencedDeadLetterQueue<D> defaultQueue() {
        //noinspection unchecked
        return (InMemorySequencedDeadLetterQueue<D>) builder().build();
    }

    @Override
    public void enqueue(@Nonnull D letter) throws DeadLetterQueueOverflowException {
        SequenceIdentifier sequenceId = letter.sequenceIdentifier();
        if (isFull(sequenceId)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue [" + letter.message() + "] for identifier ["
                            + sequenceId.combinedIdentifier() + "] since the queue is full."
            );
        }

        if (logger.isDebugEnabled()) {
            if (letter.cause().isPresent()) {
                logger.debug("Adding dead letter [{}] because [{}].", letter.message(), letter.cause().get());
            } else {
                logger.debug("Adding dead letter [{}] because the sequence identifier [{}] is already present.",
                             letter.message(), sequenceId);
            }
        }

        deadLetters.computeIfAbsent(sequenceId, id -> new ConcurrentLinkedDeque<>())
                   .addLast(letter);
    }

    @Override
    public void evict(D letter) {
        SequenceIdentifier sequenceId = letter.sequenceIdentifier();
        Deque<D> sequence = deadLetters.get(sequenceId);
        if (sequence == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot evict [{}] as there is no sequence with identifier [{}].",
                             letter.identifier(), sequenceId.combinedIdentifier());
            }
            return;
        }

        Optional<D> containedLetter = find(letter.identifier(), sequence);
        if (!containedLetter.isPresent()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot evict [{}] as it is no longer present in queue [{}].",
                             letter.identifier(), sequenceId.combinedIdentifier());
            }
            return;
        }
        sequence.remove(containedLetter.get());

        logger.trace("Evicted letter [{}].", letter.identifier());
        if (sequence.isEmpty()) {
            logger.trace("Sequence [{}] is empty and will be removed.", sequenceId);
            deadLetters.remove(sequenceId);
        }
        takenSequences.remove(sequenceId);
    }

    @Override
    public void requeue(D letter, Throwable requeueCause) {
        SequenceIdentifier sequenceId = letter.sequenceIdentifier();
        Deque<D> sequence = deadLetters.get(sequenceId);
        if (Objects.isNull(sequence)) {
            throw new NoSuchDeadLetterException(letter.identifier(), sequenceId);
        }

        Optional<D> containedLetter = find(letter.identifier(), sequence);
        if (!containedLetter.isPresent()) {
            throw new NoSuchDeadLetterException(letter.identifier(), sequenceId);
        }
        sequence.remove(containedLetter.get());

        //noinspection unchecked
        sequence.addFirst((D) letter.withCause(requeueCause));

        takenSequences.remove(sequenceId);
    }

    private Optional<D> find(String identifier, Deque<D> sequence) {
        return sequence.stream()
                       .filter(letter -> Objects.equals(letter.identifier(), identifier))
                       .findFirst();
    }

    @Override
    public boolean contains(@Nonnull SequenceIdentifier identifier) {
        if (logger.isDebugEnabled()) {
            logger.debug("Validating existence of sequence identifier [{}].", identifier.combinedIdentifier());
        }
        return deadLetters.containsKey(identifier);
    }

    @Override
    public Iterable<D> deadLetterSequence(@Nonnull SequenceIdentifier identifier) {
        return contains(identifier) ? new ArrayList<>(deadLetters.get(identifier)) : Collections.emptyList();
    }

    @Override
    public Map<SequenceIdentifier, Iterable<D>> deadLetters() {
        return new HashMap<>(deadLetters);
    }

    @Override
    public boolean isFull(@Nonnull SequenceIdentifier identifier) {
        return maximumNumberOfSequencesReached(identifier) || maximumSequenceSizeReached(identifier);
    }

    private boolean maximumNumberOfSequencesReached(SequenceIdentifier identifier) {
        return !deadLetters.containsKey(identifier) && deadLetters.keySet().size() >= maxSequences;
    }

    private boolean maximumSequenceSizeReached(SequenceIdentifier identifier) {
        return deadLetters.containsKey(identifier)
                && deadLetters.get(identifier).size() >= maxSequenceSize;
    }

    @Override
    public long maxSequences() {
        return maxSequences;
    }

    @Override
    public long maxSequenceSize() {
        return maxSequenceSize;
    }

    @Override
    public boolean process(@Nonnull Predicate<SequenceIdentifier> sequenceFilter,
                           @Nonnull Predicate<D> letterFilter,
                           Function<D, EnqueueDecision<D>> processingTask) {
        if (deadLetters.isEmpty()) {
            logger.debug("Received a request to process dead-letters but there are none.");
            return false;
        }
        logger.debug("Received a request to process matching dead-letters.");

        List<SequenceIdentifier> sequences = deadLetters.keySet()
                                                        .stream()
                                                        .filter(sequenceFilter)
                                                        .collect(Collectors.toList());

        if (sequences.isEmpty()) {
            logger.debug("Received a request to process dead-letters but there are no sequences matching the filter.");
            return false;
        }

        D letter;
        do {
            letter = getLastTouchedLetter(sequences, letterFilter);
        } while (letter != null && !takenSequences.add(letter.sequenceIdentifier()));

        if (Objects.isNull(letter)) {
            logger.debug("Received a request to process dead-letters but there are no matching dead-letters available.");
            return false;
        }

        EnqueueDecision<D> decision = processingTask.apply(letter);
        if (decision.shouldEvict()) {
            evict(letter);
        } else if (decision.shouldEnqueue()) {
            requeue(decision.addDiagnostics(letter), decision.enqueueCause().orElse(null));
            return false;
        }
        return true;
    }

    private D getLastTouchedLetter(List<SequenceIdentifier> sequenceIds, Predicate<D> letterFilter) {
        Set<Map.Entry<SequenceIdentifier, Deque<D>>> availableSequences =
                deadLetters.entrySet()
                           .stream()
                           .filter(entry -> sequenceIds.contains(entry.getKey()))
                           .collect(Collectors.toSet());

        Instant current = GenericDeadLetter.clock.instant();
        long lastTouchedSequence = Long.MAX_VALUE;
        D lastTouchedLetter = null;
        for (Map.Entry<SequenceIdentifier, Deque<D>> sequence : availableSequences) {
            D letter = sequence.getValue().peekFirst();
            if (letter != null && letterFilter.test(letter)) {
                long lastTouched = letter.lastTouched().toEpochMilli();
                if (lastTouched <= current.toEpochMilli() && lastTouched < lastTouchedSequence) {
                    lastTouchedSequence = lastTouched;
                    lastTouchedLetter = letter;
                }
            }
        }
        return lastTouchedLetter;
    }

    @Override
    public void clear(@Nonnull Predicate<SequenceIdentifier> sequenceFilter) {
        List<SequenceIdentifier> sequencesToClear = deadLetters.keySet()
                                                               .stream()
                                                               .filter(sequenceFilter)
                                                               .collect(Collectors.toList());

        sequencesToClear.forEach(sequenceId -> {
            deadLetters.get(sequenceId).clear();
            deadLetters.remove(sequenceId);
            logger.info("Cleared out all dead-letters for sequence [{}].", sequenceId);
        });
    }

    /**
     * Builder class to instantiate an {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead-letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <D> The type of {@link DeadLetter dead-letter} maintained in this {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<D extends DeadLetter<? extends Message<?>>> {

        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;

        /**
         * Sets the maximum number of queues this {@link SequencedDeadLetterQueue} may contain. This requirement
         * reflects itself as the maximum amount of unique {@link SequenceIdentifier} referencing a dead letter queue.
         * <p>
         * The given {@code maxSequences} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequences The maximum amount of sequences for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<D> maxSequences(int maxSequences) {
            assertThat(maxSequences,
                       value -> value >= 128,
                       "The maximum number of sequences should be larger or equal to 128");
            this.maxSequences = maxSequences;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter dead-letters} per sequence this {@link SequencedDeadLetterQueue}
         * can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter dead-letters} per sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<D> maxSequenceSize(int maxSequenceSize) {
            assertThat(maxSequenceSize,
                       value -> value >= 128,
                       "The maximum number of dead-letters in a sequence should be larger or equal to 128");
            this.maxSequenceSize = maxSequenceSize;
            return this;
        }

        /**
         * Initializes a {@link InMemorySequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link InMemorySequencedDeadLetterQueue} as specified through this Builder.
         */
        public InMemorySequencedDeadLetterQueue<D> build() {
            return new InMemorySequencedDeadLetterQueue<>(this);
        }

        protected void validate() {
            // No assertions required, kept for overriding
        }
    }
}
