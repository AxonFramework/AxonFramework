package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Contract describing a component that can process {@link DeadLetter dead-letters} that it has enqueued.
 * <p>
 * Should use the {@link SequencedDeadLetterQueue} as this ensures dead-lettered {@link Message Messages} are kept in
 * sequence. Thus processed in order through this component. The implementor uses its own group descriptor to match
 * with the {@link SequenceIdentifier#group()} letters it processes.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface SequencedDeadLetterProcessor<M extends Message<?>> {

    /**
     * Describes the group of this dead-letter processor.
     *
     * @return The group of this dead-letter processor.
     */
    String group();

    /**
     * Process a sequence of {@link DeadLetter dead-letters} belonging to this components {@link #group()} matching the
     * given {@code sequenceFilter} and {@code letterFilter}.
     * <p>
     * It combines the {@code sequenceFilter} through {@link Predicate#and(Predicate)} with an equals check between
     * {@link #group()} and {@link SequenceIdentifier#group()}. This ensures only dead-letters originally enqueued by
     * this component are processed by it.
     *
     * @param sequenceFilter A filter for {@link SequenceIdentifier identifiers}. Will be combined with an equals check
     *                       of the {@link #group()} with the {@link SequenceIdentifier#group()}.
     * @param letterFilter   A filter for {@link DeadLetter dead-letter} entries.
     * @return {@code true} if at least one {@link DeadLetter dead-letter} was processed successfully, {@code false}
     * otherwise.
     */
    boolean process(Predicate<SequenceIdentifier> sequenceFilter, Predicate<DeadLetter<M>> letterFilter);

    /**
     * Process a sequence of {@link DeadLetter dead-letters} belonging to this components {@link #group()} matching the
     * given {@code sequenceFilter}.
     * <p>
     * It combines the {@code sequenceFilter} through {@link Predicate#and(Predicate)} with an equals check between
     * {@link #group()} and {@link SequenceIdentifier#group()}. This ensures only dead-letters originally enqueued by
     * this component are processed by it.
     *
     * @param sequenceFilter A filter for {@link SequenceIdentifier identifiers}. Will be combined with an equals check
     *                       of the {@link #group()} with the {@link SequenceIdentifier#group()}.
     * @return {@code true} if at least one {@link DeadLetter dead-letter} was processed successfully, {@code false}
     * otherwise.
     */
    default boolean processIdentifierMatchingSequence(Predicate<SequenceIdentifier> sequenceFilter) {
        return process(sequenceFilter.and(identifier -> Objects.equals(group(), identifier.group())), letter -> true);
    }

    /**
     * Process a sequence of {@link DeadLetter dead-letters} belonging to this components {@link #group()} matching the
     * given {@code letterFilter}.
     *
     * @param letterFilter A filter for {@link DeadLetter dead-letter} entries.
     * @return {@code true} if at least one {@link DeadLetter dead-letter} was processed successfully, {@code false}
     * otherwise.
     */
    default boolean processLetterMatchingSequence(Predicate<DeadLetter<M>> letterFilter) {
        return process(identifier -> Objects.equals(group(), identifier.group()), letterFilter);
    }

    /**
     * Process any sequence of {@link DeadLetter dead-letters} belonging to this components {@link #group()}.
     *
     * @return {@code true} if at least one {@link DeadLetter dead-letter} was processed successfully, {@code false}
     * otherwise.
     */
    default boolean processAny() {
        return process(identifier -> Objects.equals(group(), identifier.group()), letter -> true);
    }
}
