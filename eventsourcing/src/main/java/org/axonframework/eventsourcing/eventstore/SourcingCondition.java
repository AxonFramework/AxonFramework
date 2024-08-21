package org.axonframework.eventsourcing.eventstore;

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.OptionalLong;

/**
 * Interface describing the condition to
 * {@link EventSourcingTransaction#source(SourcingCondition, ProcessingContext) source} events from an Event Store.
 * <p>
 * The condition has a mandatory {@link #criteria()} used to retrieve the exact sequence of events to source the
 * model(s). The {@link #start()} and {@link #end()} operations define the window of events that the
 * {@link EventSourcingTransaction} is interested in. Use these fields to retrieve slices of the model(s) to source.
 *
 * @author Marco Amann
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO does this work for single-context reading? Multi-context reading? Aggregate reading? Saga/Process reading? Query Model reading?
// Guess this will depend on the used EventCriteria at this stage. The start and end value reflect the positions on the entire stream anyhow, not a given model.
public interface SourcingCondition {

    /**
     * Construct a {@link SourcingCondition} used to source a single model instance identified by the given
     * {@code identifierName} to {@code identifierValue} combination.
     *
     * @param identifierName  The name of the identifier of the model to source.
     * @param identifierValue The value of the identifier of the model to source.
     * @return A {@link SourcingCondition} that will retrieve an event sequence for the given {@code identifierName} to
     * {@code identifierValue} combination.
     */
    static SourcingCondition singleModelFor(String identifierName,
                                            String identifierValue) {
        return singleModelFor(identifierName, identifierValue, null);
    }

    /**
     * Construct a {@link SourcingCondition} used to source a single model instance identified by the given
     * {@code identifierName} to {@code identifierValue} combination. Will start the sequence at the given {@code start}
     * value.
     *
     * @param identifierName  The name of the identifier of the model to source.
     * @param identifierValue The value of the identifier of the model to source.
     * @param start           The start position in the event sequence to retrieve of the model to source.
     * @return A {@link SourcingCondition} that will retrieve an event sequence for the given {@code identifierName} to
     * {@code identifierValue} combination.
     */
    static SourcingCondition singleModelFor(String identifierName,
                                            String identifierValue,
                                            Long start) {
        return singleModelFor(identifierName, identifierValue, start, null);
    }

    /**
     * Construct a {@link SourcingCondition} used to source a single model instance identified by the given
     * {@code identifierName} to {@code identifierValue} combination. Will start the sequence at the given {@code start}
     * value and cut it off at the given {@code end} value.
     *
     * @param identifierName  The name of the identifier of the model to source.
     * @param identifierValue The value of the identifier of the model to source.
     * @param start           The start position in the event sequence to retrieve of the model to source.
     * @param end             The end position in the event sequence to retrieve of the model to source.
     * @return A {@link SourcingCondition} that will retrieve an event sequence for the given {@code identifierName} to
     * {@code identifierValue} combination.
     */
    static SourcingCondition singleModelFor(String identifierName,
                                            String identifierValue,
                                            Long start,
                                            Long end) {
        return new SingleModelCondition(identifierName, identifierValue, start, end);
    }

    /**
     * The {@link EventCriteria} used to source an event sequence complying to its criteria.
     *
     * @return The {@link EventCriteria} used to retrieve an event sequence complying to its criteria.
     */
    EventCriteria criteria();

    /**
     * The start position in the event sequence to source. Defaults to {@code -1L} to ensure we start at the beginning
     * of the sequence's stream complying to the {@link #criteria()}.
     *
     * @return The start position in the event sequence to source.
     */
    default long start() {
        return -1L;
    }

    /**
     * The end position in the event sequence to source. Defaults to an {@link OptionalLong#empty() empty optional} to
     * ensure we take the entire event sequence complying to the {@link #criteria()}
     *
     * @return The end position in the event sequence to source.
     */
    default OptionalLong end() {
        return OptionalLong.empty();
    }
}
