/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.testutils.MockException;
import org.junit.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

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
            factory.createAggregate(UUID.randomUUID().toString(), new GenericDomainEventMessage<>("", 0, new Object()));
            fail("Expected IncompatibleAggregateException");
        } catch (IncompatibleAggregateException e) {
            // we got it
        }
    }

    @Test
    public void testParameterResolverIsRegisteredWithCreatedAggregate() {
        final ParameterResolverFactory parameterResolverFactory = mock(ParameterResolverFactory.class);
        GenericAggregateFactory<AnnotatedAggregate> factory =
                new GenericAggregateFactory<>(AnnotatedAggregate.class,
                                              parameterResolverFactory);
        final AnnotatedAggregate aggregate = factory.createAggregate("test",
                                                                       new GenericDomainEventMessage<>("test", 0,
                                                                                                       "test"));
        assertSame(parameterResolverFactory, aggregate.getParameterResolverFactory());
    }

    @Test
    public void testInitializeFromAggregateSnapshot() {
        StubAggregate aggregate = new StubAggregate("stubId");
        aggregate.doSomething();
        DomainEventMessage<StubAggregate> snapshotMessage = new GenericDomainEventMessage<>(
                aggregate.getIdentifier(), aggregate.getVersion(), aggregate);
        GenericAggregateFactory<StubAggregate> factory = new GenericAggregateFactory<>(StubAggregate.class);
        assertSame(aggregate, factory.createAggregate(aggregate.getIdentifier(), snapshotMessage));
    }

    private static class UnsuitableAggregate extends AbstractEventSourcedAggregateRoot {

        private UnsuitableAggregate(Object uuid) {
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            return null;
        }

        @Override
        protected void handle(EventMessage event) {
        }

        @Override
        public String getIdentifier() {
            return "unsuitableAggregateId";
        }
    }

    private static class ExceptionThrowingAggregate extends AbstractEventSourcedAggregateRoot {

        @AggregateIdentifier
        private String identifier;

        private ExceptionThrowingAggregate() {
            throw new MockException();
        }

        @Override
        protected void handle(EventMessage event) {
        }

        @Override
        public String getIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            return null;
        }
    }

    private static class AnnotatedAggregate extends AbstractAnnotatedAggregateRoot {

        public ParameterResolverFactory getParameterResolverFactory() {
            return super.createParameterResolverFactory();
        }

    }
}
