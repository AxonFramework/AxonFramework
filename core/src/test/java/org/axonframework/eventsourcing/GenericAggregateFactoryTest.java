/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubAggregate;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericAggregateFactoryTest {

    @Test(expected = IncompatibleAggregateException.class)
    public void testInitializeRepository_NoSuitableConstructor() {
        new GenericAggregateFactory<UnsuitableAggregate>(UnsuitableAggregate.class);
    }

    @Test
    public void testInitializeRepository_ConstructorNotCallable() {
        GenericAggregateFactory<ExceptionThrowingAggregate> repository =
                new GenericAggregateFactory<ExceptionThrowingAggregate>(ExceptionThrowingAggregate.class);
        try {
            repository.createAggregate(AggregateIdentifierFactory.randomIdentifier(), null);
            fail("Expected IncompatibleAggregateException");
        }
        catch (IncompatibleAggregateException e) {
            // we got it
        }
    }

    @Test
    public void testAggregateTypeIsSimpleName() {
        GenericAggregateFactory<StubAggregate> factory = new GenericAggregateFactory<StubAggregate>(StubAggregate.class);
        assertEquals("StubAggregate", factory.getTypeIdentifier());
    }

    @Test
    public void testInitializeFromAggregateSnapshot() {
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        aggregate.commitEvents();
        AggregateSnapshot<StubAggregate> snapshot = new AggregateSnapshot<StubAggregate>(aggregate);
        GenericAggregateFactory<StubAggregate> factory = new GenericAggregateFactory<StubAggregate>(StubAggregate.class);
        assertEquals("StubAggregate", factory.getTypeIdentifier());
        assertSame(aggregate, factory.createAggregate(aggregate.getIdentifier(), snapshot));
    }

    private static class UnsuitableAggregate extends AbstractEventSourcedAggregateRoot {

        @Override
        protected void handle(DomainEvent event) {
        }
    }

    private static class ExceptionThrowingAggregate
            extends AbstractEventSourcedAggregateRoot {

        private ExceptionThrowingAggregate(AggregateIdentifier uuid) {
            throw new RuntimeException("Mock");
        }

        @Override
        protected void handle(DomainEvent event) {
        }
    }

}
