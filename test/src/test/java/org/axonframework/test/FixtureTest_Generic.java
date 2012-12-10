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

package org.axonframework.test;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventstore.EventStoreException;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Generic {

    private FixtureConfiguration<StandardAggregate> fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture(StandardAggregate.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConfigureCustomAggregateFactory() {
        AggregateFactory<StandardAggregate> mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.createAggregate(isA(String.class), isA(DomainEventMessage.class)))
                .thenReturn(new StandardAggregate());
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.registerAggregateFactory(mockAggregateFactory);

        fixture.given(new MyEvent("id1", 1))
                .when(new TestCommand("id1"));

        verify(mockAggregateFactory).createAggregate(eq("id1"), isA(DomainEventMessage.class));
    }

    @Test
    public void testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.given()
               .when(new CreateAggregateCommand());
    }

    @Test(expected = FixtureExecutionException.class)
    public void testInjectResources_CommandHandlerAlreadyRegistered() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.registerInjectableResource("I am injectable");
    }

    @Test
    public void testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.given(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
               .when(new TestCommand("AggregateId"))
               .expectEvents(new MyEvent("AggregateId", 3));

        DomainEventStream events = fixture.getEventStore().readEvents("StandardAggregate", "AggregateId");
        for (int t=0;t<3;t++) {
            assertTrue(events.hasNext());
            DomainEventMessage next = events.next();
            assertEquals("AggregateId", next.getAggregateIdentifier());
            assertEquals(t, next.getSequenceNumber());
        }
    }

    @Test
    public void testReadAggregate_WrongIdentifier() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        TestExecutor exec = fixture.given(new MyEvent("AggregateId", 1));
        try {
            exec.when(new TestCommand("OtherIdentifier"));
            fail("Expected an AssertionError");
        } catch (AssertionError e) {
            assertTrue("Wrong message. Was: " + e.getMessage(), e.getMessage().contains("OtherIdentifier"));
            assertTrue("Wrong message. Was: " + e.getMessage(), e.getMessage().contains("AggregateId"));
        }
    }

    @Test(expected = EventStoreException.class)
    public void testFixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        fixture.getEventStore().appendEvents("whatever", new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(UUID.randomUUID(), 0, new StubDomainEvent()),
                new GenericDomainEventMessage<StubDomainEvent>(UUID.randomUUID(), 0, new StubDomainEvent())));
    }

    @Test(expected = EventStoreException.class)
    public void testFixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        UUID identifier = UUID.randomUUID();
        fixture.getEventStore().appendEvents("whatever", new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(identifier, 0, new StubDomainEvent()),
                new GenericDomainEventMessage<StubDomainEvent>(identifier, 2, new StubDomainEvent())));
    }

    private class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
