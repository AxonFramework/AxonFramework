package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;

import static org.junit.jupiter.api.Assertions.*;

class LegacyJpaEventStorageEngineTest extends AggregateBasedStorageEngineTestSuite<LegacyJpaEventStorageEngine> {

    @Override
    protected LegacyJpaEventStorageEngine buildStorageEngine() throws Exception {
        return new LegacyJpaEventStorageEngine();
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return null;
    }
}