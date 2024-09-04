package org.axonframework.eventsourcing.eventstore;

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.OptionalLong;

/**
 * Interface describing the condition to
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) source} events from an Event Store.
 * <p>
 * The condition has a mandatory {@link #criteria()} used to retrieve the exact sequence of events to source the
 * model(s). The {@link #start()} and {@link #end()} operations define the window of events that the
 * {@link EventStoreTransaction} is interested in. Use these fields to retrieve slices of the model(s) to source.
 *
 * @author Marco Amann
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface SourcingCondition {

    /**
     * Default {@link Index#key()} used when constructing a {@link SourcingCondition} tailored towards an aggregate.
     * <p>
     * Represents the value {@code "aggregateIdentifier"}.
     */
    String AGGREGATE_IDENTIFIER_NAME = "aggregateIdentifier";

    /**
     * Construct a {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     * <p>
     * The {@link Index} will use the {@link #AGGREGATE_IDENTIFIER_NAME} as the {@link Index#key()}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to source.
     * @return A {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     */
    static SourcingCondition aggregateFor(String aggregateIdentifier) {
        return aggregateFor(aggregateIdentifier, -1L);
    }

    /**
     * Construct a {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     * <p>
     * Will start the sequence at the given {@code start} value. The {@link Index} will use the
     * {@link #AGGREGATE_IDENTIFIER_NAME} as the {@link Index#key()}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to source.
     * @param start               The start position in the event sequence to retrieve of the aggregate to source.
     * @return A {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     */
    static SourcingCondition aggregateFor(String aggregateIdentifier,
                                          Long start) {
        return aggregateFor(aggregateIdentifier, start, Long.MAX_VALUE);
    }

    /**
     * Construct a {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     * <p>
     * Will start the sequence at the given {@code start} value and cut it off at the given {@code end} value. The
     * {@link Index} will use the {@link #AGGREGATE_IDENTIFIER_NAME} as the {@link Index#key()}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to source.
     * @param start               The start position in the event sequence to retrieve of the aggregate to source.
     * @param end                 The end position in the event sequence to retrieve of the aggregate to source.
     * @return A {@link SourcingCondition} used to source an aggregate instance identified by the given
     * {@code aggregateIdentifier}.
     */
    static SourcingCondition aggregateFor(String aggregateIdentifier,
                                          Long start,
                                          Long end) {
        return singleModelFor(AGGREGATE_IDENTIFIER_NAME, aggregateIdentifier, start, end);
    }

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
