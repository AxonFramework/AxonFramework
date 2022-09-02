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

import org.axonframework.common.StringUtils;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * In-memory implementation of the {@link SequencedDeadLetterQueue}.
 * <p>
 * Maintains a {@link Deque} per unique "sequence identifier." The maximum amount of {@code Deques} contained by this
 * {@code SequencedDeadLetterQueue} is {@code 1024} (configurable through {@link Builder#maxSequences(int)}). The
 * maximum amount of {@link DeadLetter dead-letters} per sequence also defaults to {@code 1024} (configurable through
 * {@link Builder#maxSequenceSize(int)}).
 *
 * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead-letter} of this
 *            {@link SequencedDeadLetterQueue}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemorySequencedDeadLetterQueue<M extends Message<?>> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<String, Deque<DeadLetter<? extends M>>> deadLetters = new ConcurrentHashMap<>();
    private final Set<String> takenSequences = new ConcurrentSkipListSet<>();

    private final int maxSequences;
    private final int maxSequenceSize;

    /**
     * Instantiate an in-memory {@link SequencedDeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link InMemorySequencedDeadLetterQueue} instance.
     */
    protected InMemorySequencedDeadLetterQueue(Builder<M> builder) {
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
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead-letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <M extends Message<?>> Builder<M> builder() {
        return new Builder<>();
    }

    /**
     * Construct a default {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead-letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead-letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A default {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <M extends Message<?>> InMemorySequencedDeadLetterQueue<M> defaultQueue() {
        return InMemorySequencedDeadLetterQueue.<M>builder().build();
    }

    @Override
    public void enqueue(@Nonnull Object sequenceIdentifier,
                        @Nonnull DeadLetter<? extends M> letter) throws DeadLetterQueueOverflowException {
        synchronized (deadLetters) {
            if (isFull(sequenceIdentifier)) {
                throw new DeadLetterQueueOverflowException(sequenceIdentifier);
            }

            if (logger.isDebugEnabled()) {
                Optional<Cause> optionalCause = letter.cause();
                if (optionalCause.isPresent()) {
                    logger.debug("Adding dead letter [{}] because [{}].", letter.message(), optionalCause.get());
                } else {
                    logger.debug("Adding dead letter [{}] because the sequence identifier [{}] is already present.",
                                 letter.message(), sequenceIdentifier);
                }
            }

            deadLetters.computeIfAbsent(toIdentifier(sequenceIdentifier), id -> new ConcurrentLinkedDeque<>())
                       .addLast(letter);
        }
    }

    @Override
    public void evict(DeadLetter<? extends M> letter) {
        synchronized (deadLetters) {
            Optional<Map.Entry<String, Deque<DeadLetter<? extends M>>>> optionalSequence =
                    deadLetters.entrySet()
                               .stream()
                               .filter(sequence -> sequence.getValue().remove(letter))
                               .findFirst();

            if (optionalSequence.isPresent()) {
                String sequenceId = optionalSequence.get().getKey();
                if (deadLetters.get(sequenceId).isEmpty()) {
                    logger.trace("Sequence [{}] is empty and will be removed.", sequenceId);
                    deadLetters.remove(sequenceId);
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Evicted letter [{}] for sequence [{}].", letter, sequenceId);
                }
            } else if (logger.isDebugEnabled()) {
                logger.debug("Cannot evict [{}] as it could not be found in this queue.", letter);
            }
        }
    }

    @Override
    public void requeue(
            @Nonnull DeadLetter<? extends M> letter,
            @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater
    ) throws NoSuchDeadLetterException {
        synchronized (deadLetters) {
            Optional<Map.Entry<String, Deque<DeadLetter<? extends M>>>> optionalSequence =
                    deadLetters.entrySet()
                               .stream()
                               .filter(sequence -> sequence.getValue().remove(letter))
                               .findFirst();

            if (optionalSequence.isPresent()) {
                String sequenceId = optionalSequence.get().getKey();
                deadLetters.get(sequenceId)
                           .addFirst(letterUpdater.apply(letter.markTouched()));
                if (logger.isTraceEnabled()) {
                    logger.trace("Requeued letter [{}] for sequence [{}].", letter, sequenceId);
                }
            } else {
                throw new NoSuchDeadLetterException(
                        "Cannot requeue [" + letter + "] since there is not matching entry in this queue."
                );
            }
        }
    }

    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
        if (logger.isDebugEnabled()) {
            logger.debug("Validating existence of sequence identifier [{}].", sequenceIdentifier);
        }
        return contains(toIdentifier(sequenceIdentifier));
    }

    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return contains(identifier) ? new ArrayList<>(deadLetters.get(identifier)) : Collections.emptyList();
    }

    private boolean contains(String identifier) {
        synchronized (deadLetters) {
            return deadLetters.containsKey(identifier);
        }
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        return new ArrayList<>(deadLetters.values());
    }

    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return maximumNumberOfSequencesReached(identifier) || maximumSequenceSizeReached(identifier);
    }

    private boolean maximumNumberOfSequencesReached(String identifier) {
        return !deadLetters.containsKey(identifier) && deadLetters.keySet().size() >= maxSequences;
    }

    private boolean maximumSequenceSizeReached(String identifier) {
        return deadLetters.containsKey(identifier) && deadLetters.get(identifier).size() >= maxSequenceSize;
    }

    @Override
    public long size() {
        return deadLetters.values()
                          .stream()
                          .mapToLong(Deque::size)
                          .sum();
    }

    @Override
    public long sequenceSize(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return contains(identifier) ? deadLetters.get(identifier).size() : 0L;
    }

    private static String toIdentifier(Object sequenceIdentifier) {
        return sequenceIdentifier instanceof String
                ? (String) sequenceIdentifier
                : Integer.toString(sequenceIdentifier.hashCode());
    }

    @Override
    public long amountOfSequences() {
        return deadLetters.size();
    }

    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        synchronized (deadLetters) {
            if (deadLetters.isEmpty()) {
                logger.debug("Received a request to process dead-letters but there are none.");
                return false;
            }
            logger.debug("Received a request to process matching dead-letters.");

            Map<String, DeadLetter<? extends M>> sequenceIdsToLetter =
                    deadLetters.entrySet()
                               .stream()
                               .filter(entry -> !takenSequences.contains(entry.getKey()))
                               .filter(sequence -> sequenceFilter.test(sequence.getValue().getFirst()))
                               .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));

            if (sequenceIdsToLetter.isEmpty()) {
                logger.debug(
                        "Received a request to process dead-letters but there are no sequences matching the filter.");
                return false;
            }

            logger.info("Got sequences: {}", sequenceIdsToLetter.keySet());
            String sequenceId = getLastTouchedSequence(sequenceIdsToLetter);
            boolean freshlyTaken = takenSequences.add(sequenceId);
            while (sequenceId != null && !freshlyTaken) {
                sequenceIdsToLetter.remove(sequenceId);
                sequenceId = getLastTouchedSequence(sequenceIdsToLetter);
                freshlyTaken = takenSequences.add(sequenceId);
            }

            if (StringUtils.emptyOrNull(sequenceId)) {
                logger.debug("Received a request to process dead-letters but there are none left to process.");
                return false;
            }

            try {
                while (deadLetters.get(sequenceId) != null && !deadLetters.get(sequenceId).isEmpty()) {
                    DeadLetter<? extends M> letter = deadLetters.get(sequenceId).getFirst();
                    EnqueueDecision<M> decision = processingTask.apply(letter);

                    if (decision.shouldEnqueue()) {
                        requeue(letter,
                                l -> decision.withDiagnostics(l).withCause(decision.enqueueCause().orElse(null)));
                        return false;
                    } else {
                        evict(letter);
                    }
                }
                return true;
            } finally {
                takenSequences.remove(sequenceId);
            }
        }
    }

    private String getLastTouchedSequence(Map<String, DeadLetter<? extends M>> sequenceIdsToLetter) {
        Instant current = GenericDeadLetter.clock.instant();
        long lastTouchedSequence = Long.MAX_VALUE;
        String lastTouchedSequenceId = null;
        for (Map.Entry<String, DeadLetter<? extends M>> sequenceIdToLetter : sequenceIdsToLetter.entrySet()) {
            DeadLetter<? extends M> letter = sequenceIdToLetter.getValue();
            if (letter != null) {
                long lastTouched = letter.lastTouched().toEpochMilli();
                if (lastTouched <= current.toEpochMilli() && lastTouched < lastTouchedSequence) {
                    lastTouchedSequence = lastTouched;
                    lastTouchedSequenceId = sequenceIdToLetter.getKey();
                }
            }
        }
        return lastTouchedSequenceId;
    }

    @Override
    public void clear() {
        List<String> sequencesToClear = new ArrayList<>(deadLetters.keySet());

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
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead-letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<M extends Message<?>> {

        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;

        /**
         * Sets the maximum number of sequences this {@link SequencedDeadLetterQueue} may contain. This requirement
         * reflects itself as the maximum amount of unique "sequence identifiers."
         * <p>
         * The given {@code maxSequences} is required to be a strictly positive number. It defaults to {@code 1024}.
         *
         * @param maxSequences The maximum amount of sequences for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> maxSequences(int maxSequences) {
            assertStrictPositive(maxSequences, "The maximum number of sequences should be a strictly positive number");
            this.maxSequences = maxSequences;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter dead-letters} per sequence this {@link SequencedDeadLetterQueue}
         * can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a strictly positive number. It defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter dead-letters} per sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(
                    maxSequenceSize,
                    "The maximum number of dead-letters in a sequence should be a strictly positive number"
            );
            this.maxSequenceSize = maxSequenceSize;
            return this;
        }

        /**
         * Initializes a {@link InMemorySequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link InMemorySequencedDeadLetterQueue} as specified through this Builder.
         */
        public InMemorySequencedDeadLetterQueue<M> build() {
            return new InMemorySequencedDeadLetterQueue<>(this);
        }

        protected void validate() {
            // No assertions required, kept for overriding
        }
    }
}
