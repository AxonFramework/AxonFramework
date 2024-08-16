package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;

import java.util.OptionalLong;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO does this work for single-context reading? Multi-context reading? Aggregate reading? Saga/Process reading? Query Model reading?
// Guess this will depend on the used EventCriteria at this stage. The start and end value reflect the positions on the entire stream anyhow, not a given model.
public interface SourcingCondition {

    /**
     * @return
     */
    EventCriteria criteria();

    /**
     * @return
     */
    default long start() {
        return -1;
    }

    /**
     * @return
     */
    default OptionalLong end() {
        return OptionalLong.empty();
    }

    /**
     *
     * @param identifier
     * @return
     */
    static SourcingCondition singleModelFor(String identifier) {
        return singleModelFor(identifier, null);
    }

    /**
     *
     * @param identifier
     * @param start
     * @return
     */
    static SourcingCondition singleModelFor(String identifier, Long start) {
        return singleModelFor(identifier, start, null);
    }

    /**
     *
     * @param identifier
     * @param start
     * @param end
     * @return
     */
    static SourcingCondition singleModelFor(String identifier, Long start, Long end) {
        return new SingleModelCondition(identifier, start, end);
    }
}
