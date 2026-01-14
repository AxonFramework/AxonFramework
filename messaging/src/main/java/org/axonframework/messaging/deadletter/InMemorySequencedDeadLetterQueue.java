/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.messaging.core.Message;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import jakarta.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * In-memory implementation of the {@link SequencedDeadLetterQueue}.
 * <p>
 * Maintains a {@link Deque} per unique "sequence identifier." The maximum amount of {@code Deques} contained by this
 * {@code SequencedDeadLetterQueue} is {@code 1024} (configurable through {@link Builder#maxSequences(int)}). The
 * maximum amount of {@link DeadLetter dead letters} per sequence also defaults to {@code 1024} (configurable through
 * {@link Builder#maxSequenceSize(int)}).
 * <p>
 * All operations return {@link CompletableFuture} for API consistency with the {@link SequencedDeadLetterQueue}
 * interface, but execute synchronously since this is an in-memory implementation.
 *
 * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
 *            {@link SequencedDeadLetterQueue}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemorySequencedDeadLetterQueue<M extends Message> implements SequencedDeadLetterQueue<M> {

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
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <M extends Message> Builder<M> builder() {
        return new Builder<>();
    }

    /**
     * Construct a default {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A default {@link InMemorySequencedDeadLetterQueue}.
     */
    public static <M extends Message> InMemorySequencedDeadLetterQueue<M> defaultQueue() {
        return InMemorySequencedDeadLetterQueue.<M>builder().build();
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter) {
        return isFullSync(sequenceIdentifier)
                ? CompletableFuture.failedFuture(new DeadLetterQueueOverflowException(sequenceIdentifier))
                : CompletableFuture.completedFuture(enqueueSync(sequenceIdentifier, letter));
    }

    private Void enqueueSync(Object sequenceIdentifier, DeadLetter<? extends M> letter) {
        if (logger.isDebugEnabled()) {
            Optional<Cause> optionalCause = letter.cause();
            if (optionalCause.isPresent()) {
                logger.debug("Adding dead letter with message id [{}] because [{}].",
                             letter.message().identifier(),
                             optionalCause.get().type());
            } else {
                logger.debug(
                        "Adding dead letter with message id [{}] because the sequence identifier [{}] is already present.",
                        letter.message().identifier(),
                        sequenceIdentifier);
            }
        }

        synchronized (deadLetters) {
            deadLetters.computeIfAbsent(toIdentifier(sequenceIdentifier), id -> new ConcurrentLinkedDeque<>())
                       .addLast(letter);
        }
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> evict(@Nonnull DeadLetter<? extends M> letter) {
        return CompletableFuture.completedFuture(evictSync(letter));
    }

    private Void evictSync(DeadLetter<? extends M> letter) {
        Optional<Map.Entry<String, Deque<DeadLetter<? extends M>>>> optionalSequence =
                deadLetters.entrySet()
                           .stream()
                           .filter(sequence -> sequence.getValue().remove(letter))
                           .findFirst();

        if (optionalSequence.isPresent()) {
            synchronized (deadLetters) {
                String sequenceId = optionalSequence.get().getKey();
                if (deadLetters.get(sequenceId).isEmpty()) {
                    logger.trace("Sequence with id [{}] is empty and will be removed.", sequenceId);
                    deadLetters.remove(sequenceId);
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Evicted letter with message id [{}] for sequence id [{}].",
                                 letter.message().identifier(), sequenceId);
                }
            }
        } else if (logger.isDebugEnabled()) {
            logger.debug("Cannot evict letter with message id [{}] as it could not be found in this queue.",
                         letter.message().identifier());
        }
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> requeue(@Nonnull DeadLetter<? extends M> letter,
                                           @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater) {
        try {
            return CompletableFuture.completedFuture(requeueSync(letter, letterUpdater));
        } catch (NoSuchDeadLetterException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Void requeueSync(DeadLetter<? extends M> letter,
                             UnaryOperator<DeadLetter<? extends M>> letterUpdater) {
        Optional<Map.Entry<String, Deque<DeadLetter<? extends M>>>> optionalSequence =
                deadLetters.entrySet()
                           .stream()
                           .filter(sequence -> sequence.getValue().remove(letter))
                           .findFirst();

        if (optionalSequence.isPresent()) {
            synchronized (deadLetters) {
                String sequenceId = optionalSequence.get().getKey();
                deadLetters.get(sequenceId)
                           .addFirst(letterUpdater.apply(letter.markTouched()));
                if (logger.isTraceEnabled()) {
                    logger.trace("Requeued letter [{}] for sequence [{}].",
                                 letter.message().identifier(), sequenceId);
                }
            }
        } else {
            throw new NoSuchDeadLetterException(
                    "Cannot requeue [" + letter.message().identifier()
                            + "] since there is no matching entry in this queue."
            );
        }
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> contains(@Nonnull Object sequenceIdentifier) {
        if (logger.isDebugEnabled()) {
            logger.debug("Validating existence of sequence identifier [{}].", sequenceIdentifier);
        }
        return CompletableFuture.completedFuture(containsSync(toIdentifier(sequenceIdentifier)));
    }

    private boolean containsSync(String identifier) {
        synchronized (deadLetters) {
            return deadLetters.containsKey(identifier);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<DeadLetter<? extends M>>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        Iterable<DeadLetter<? extends M>> result = containsSync(identifier)
                ? new ArrayList<>(deadLetters.get(identifier))
                : Collections.emptyList();
        return CompletableFuture.completedFuture(result);
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends M>>>> deadLetters() {
        return CompletableFuture.completedFuture(new ArrayList<>(deadLetters.values()));
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> isFull(@Nonnull Object sequenceIdentifier) {
        return CompletableFuture.completedFuture(isFullSync(sequenceIdentifier));
    }

    private boolean isFullSync(Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return maximumNumberOfSequencesReached(identifier) || maximumSequenceSizeReached(identifier);
    }

    private boolean maximumNumberOfSequencesReached(String identifier) {
        return !deadLetters.containsKey(identifier) && deadLetters.keySet().size() >= maxSequences;
    }

    private boolean maximumSequenceSizeReached(String identifier) {
        return deadLetters.containsKey(identifier) && deadLetters.get(identifier).size() >= maxSequenceSize;
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> size() {
        long totalSize = deadLetters.values()
                                    .stream()
                                    .mapToLong(Deque::size)
                                    .sum();
        return CompletableFuture.completedFuture(totalSize);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> sequenceSize(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        long size = containsSync(identifier) ? deadLetters.get(identifier).size() : 0L;
        return CompletableFuture.completedFuture(size);
    }

    private static String toIdentifier(Object sequenceIdentifier) {
        return sequenceIdentifier instanceof String
                ? (String) sequenceIdentifier
                : Integer.toString(sequenceIdentifier.hashCode());
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> amountOfSequences() {
        return CompletableFuture.completedFuture((long) deadLetters.size());
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> process(
            @Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
            @Nonnull Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask) {
        if (deadLetters.isEmpty()) {
            logger.debug("Received a request to process dead letters but there are none.");
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Received a request to process matching dead letters.");

        Map<String, DeadLetter<? extends M>> sequenceIdsToLetter =
                deadLetters.entrySet()
                           .stream()
                           .filter(entry -> !takenSequences.contains(entry.getKey()))
                           .filter(sequence -> sequenceFilter.test(sequence.getValue().getFirst()))
                           .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));

        if (sequenceIdsToLetter.isEmpty()) {
            logger.debug("Received a request to process dead letters but there are no sequences matching the filter.");
            return CompletableFuture.completedFuture(false);
        }

        String sequenceId = getLastTouchedSequence(sequenceIdsToLetter);
        if (sequenceId == null) {
            logger.debug("Received a request to process dead letters but there are none with valid timestamps.");
            return CompletableFuture.completedFuture(false);
        }
        boolean freshlyTaken = takenSequences.add(sequenceId);
        while (!freshlyTaken) {
            sequenceIdsToLetter.remove(sequenceId);
            sequenceId = getLastTouchedSequence(sequenceIdsToLetter);
            if (sequenceId == null) {
                break;
            }
            freshlyTaken = takenSequences.add(sequenceId);
        }

        if (StringUtils.emptyOrNull(sequenceId)) {
            logger.debug("Received a request to process dead letters but there are none left to process.");
            return CompletableFuture.completedFuture(false);
        }

        final String finalSequenceId = sequenceId;
        return processSequence(finalSequenceId, processingTask)
                .whenComplete((result, error) -> takenSequences.remove(finalSequenceId));
    }

    private CompletableFuture<Boolean> processSequence(
            String sequenceId,
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask) {
        Deque<DeadLetter<? extends M>> sequence = deadLetters.get(sequenceId);
        if (sequence == null || sequence.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        DeadLetter<? extends M> letter = sequence.getFirst();
        return processingTask.apply(letter)
                             .thenCompose(decision -> {
                                 if (decision.shouldEnqueue()) {
                                     return requeue(letter,
                                                    l -> decision.withDiagnostics(l)
                                                                 .withCause(decision.enqueueCause().orElse(null)))
                                             .thenApply(v -> false);
                                 } else {
                                     return evict(letter)
                                             .thenCompose(v -> processSequence(sequenceId, processingTask));
                                 }
                             });
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

    @Nonnull
    @Override
    public CompletableFuture<Void> clear() {
        List<String> sequencesToClear = new ArrayList<>(deadLetters.keySet());

        sequencesToClear.forEach(sequenceId -> {
            deadLetters.get(sequenceId).clear();
            deadLetters.remove(sequenceId);
            logger.info("Cleared out all dead letters for sequence [{}].", sequenceId);
        });
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Builder class to instantiate an {@link InMemorySequencedDeadLetterQueue}.
     * <p>
     * The maximum number of sequences defaults to {@code 1024} and the maximum amount of dead letters inside a sequence
     * defaults to {@code 1024}.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<M extends Message> {

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
         * Sets the maximum amount of {@link DeadLetter dead letters} per sequence this {@link SequencedDeadLetterQueue}
         * can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a strictly positive number. It defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter dead letters} per sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(
                    maxSequenceSize,
                    "The maximum number of dead letters in a sequence should be a strictly positive number"
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
