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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubAggregate;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericEventSourcingRepositoryTest {

    @Test
    public void testCreateInstance() {
        GenericEventSourcingRepository<StubAggregate> repository =
                new GenericEventSourcingRepository<StubAggregate>(StubAggregate.class);

        assertEquals("StubAggregate", repository.getTypeIdentifier());
        StubAggregate actualAggregate = repository.instantiateAggregate(UUID.randomUUID(), null);
        assertNotNull(actualAggregate);
    }

    @Test(expected = IncompatibleAggregateException.class)
    public void testInitializeRepository_NoSuitableConstructor() {
        new GenericEventSourcingRepository<UnsuitableAggregate>(UnsuitableAggregate.class);
    }

    @Test
    public void testInitializeRepository_ConstructorNotCallable() {
        GenericEventSourcingRepository<ExceptionThrowingAggregate> repository =
                new GenericEventSourcingRepository<ExceptionThrowingAggregate>(ExceptionThrowingAggregate.class);
        try {
            repository.instantiateAggregate(UUID.randomUUID(), null);
            fail("Expected IncompatibleAggregateException");
        }
        catch (IncompatibleAggregateException e) {
            // we got it
        }
    }

    private static class UnsuitableAggregate extends AbstractEventSourcedAggregateRoot {

        @Override
        protected void handle(DomainEvent event) {
        }
    }

    private static class ExceptionThrowingAggregate extends AbstractEventSourcedAggregateRoot {

        private ExceptionThrowingAggregate(UUID uuid) {
            throw new RuntimeException("Mock");
        }

        @Override
        protected void handle(DomainEvent event) {
        }
    }
}
