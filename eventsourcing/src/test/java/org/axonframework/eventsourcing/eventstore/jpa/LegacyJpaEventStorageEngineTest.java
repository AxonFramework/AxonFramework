package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;

import static org.junit.jupiter.api.Assertions.*;

class LegacyJpaEventStorageEngineTest extends AggregateBasedStorageEngineTestSuite<LegacyJpaEventStorageEngine> {

    @Override
    protected LegacyJpaEventStorageEngine buildStorageEngine() throws Exception {
        var testSerializer = TestSerializer.JACKSON.getSerializer();
        return new LegacyJpaEventStorageEngine(null, null, testSerializer, testSerializer);
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return null;
    }
}