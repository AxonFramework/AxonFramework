/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.StubAggregate;
import org.axonframework.common.MockException;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Allard Buijze
 */
public class GenericAggregateFactoryTest {

    @Test(expected = IncompatibleAggregateException.class)
    public void testInitializeRepository_NoSuitableConstructor() {
        new GenericAggregateFactory<>(UnsuitableAggregate.class);
    }

    @Test
    public void testInitializeRepository_ConstructorNotCallable() {
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
    public void testInitializeFromAggregateSnapshot() {
        StubAggregate aggregate = new StubAggregate("stubId");
        DomainEventMessage<StubAggregate> snapshotMessage = new GenericDomainEventMessage<>("type", aggregate.getIdentifier(),
                                                                                            2, aggregate);
        GenericAggregateFactory<StubAggregate> factory = new GenericAggregateFactory<>(StubAggregate.class);
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

}
