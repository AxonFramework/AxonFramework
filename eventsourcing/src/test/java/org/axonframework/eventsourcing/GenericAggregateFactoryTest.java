/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.utils.MockException;
import org.axonframework.eventsourcing.utils.StubAggregate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class GenericAggregateFactoryTest {

    @Test
    void initializeRepository_NoSuitableConstructor() {
        assertThrows(IncompatibleAggregateException.class, () -> new GenericAggregateFactory<>(UnsuitableAggregate.class));
    }

    @Test
    void initializeRepository_ConstructorNotCallable() {
        GenericAggregateFactory<ExceptionThrowingAggregate> factory =
                new GenericAggregateFactory<>(ExceptionThrowingAggregate.class);
        try {
            factory.createAggregateRoot(UUID.randomUUID().toString(), new GenericDomainEventMessage<>("type", "", 0, new Object()));
            fail("Expected IncompatibleAggregateException");
        } catch (IncompatibleAggregateException e) {
            // we got it
        }
    }

    @Test
    void initializeFromAggregateSnapshot() {
        StubAggregate aggregate = new StubAggregate("stubId");
        DomainEventMessage<StubAggregate> snapshotMessage = new GenericDomainEventMessage<>("type", aggregate.getIdentifier(),
                                                                                            2, aggregate);
        GenericAggregateFactory<StubAggregate> factory = new GenericAggregateFactory<>(StubAggregate.class);
        assertSame(aggregate, factory.createAggregateRoot(aggregate.getIdentifier(), snapshotMessage));
    }

    /**
     * Verify that {@link GenericAggregateFactory#doCreateAggregate} is not called unnecessarily.
     */
    @Test
    void initializeFromAggregateSnapshot_AvoidCallingDoCreateAggregate() {
        StubAggregate aggregate = new StubAggregate("stubId");
        DomainEventMessage<StubAggregate> snapshotMessage = new GenericDomainEventMessage<>("type",
                aggregate.getIdentifier(),
                2, aggregate);
        AggregateFactory<StubAggregate> factory = new RogueAggregateFactory(StubAggregate.class);
        assertSame(aggregate, factory.createAggregateRoot(aggregate.getIdentifier(), snapshotMessage));
    }

    private static class UnsuitableAggregate {

        private UnsuitableAggregate(Object uuid) {
        }
    }

    private static class ExceptionThrowingAggregate {

        private ExceptionThrowingAggregate() {
            throw new MockException();
        }

    }

    private static class RogueAggregateFactory extends GenericAggregateFactory<StubAggregate> {
        public RogueAggregateFactory(Class<StubAggregate> aggregateType) {
            super(aggregateType);
        }

        @Override
        protected StubAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            throw new AssertionError("Forced error");
        }
    }


}
