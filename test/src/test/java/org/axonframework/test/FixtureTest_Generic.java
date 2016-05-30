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

package org.axonframework.test;

import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.IncompatibleAggregateException;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Generic {

    private FixtureConfiguration<StandardAggregate> fixture;
    private AggregateFactory<StandardAggregate> mockAggregateFactory;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture(StandardAggregate.class);
        fixture.setReportIllegalStateChange(false);
        mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.getAggregateType()).thenReturn(StandardAggregate.class);
        when(mockAggregateFactory.createAggregateRoot(isA(String.class), isA(DomainEventMessage.class)))
                .thenReturn(new StandardAggregate("id1"));
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            fail("Test failed to close Unit of Work!!");
            CurrentUnitOfWork.get().rollback();
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConfigureCustomAggregateFactory() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));

        fixture.given(new MyEvent("id1", 1))
                .when(new TestCommand("id1"));

        verify(mockAggregateFactory).createAggregateRoot(eq("id1"), isA(DomainEventMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IncompatibleAggregateException.class)
    public void testConfigurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGiven() {
        fixture.given(new MyEvent("id1", 1));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IncompatibleAggregateException.class)
    public void testConfigurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGetRepository() {
        fixture.getRepository();
    }

    @Test
    public void testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.givenNoPriorActivity()
                .when(new CreateAggregateCommand());
    }

    @Test
    public void testStoringExistingAggregateGeneratesException() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.given(new MyEvent("aggregateId", 1))
                .when(new CreateAggregateCommand("aggregateId"))
                .expectException(EventStoreException.class);
    }

    @Test(expected = FixtureExecutionException.class)
    public void testInjectResources_CommandHandlerAlreadyRegistered() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.registerInjectableResource("I am injectable");
    }

    @Test
    public void testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.given(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
                .when(new TestCommand("AggregateId"))
                .expectEvents(new MyEvent("AggregateId", 3));

        DomainEventStream events = fixture.getEventStore().readEvents("AggregateId");
        for (int t = 0; t < 3; t++) {
            assertTrue(events.hasNext());
            DomainEventMessage next = events.next();
            assertEquals("AggregateId", next.getAggregateIdentifier());
            assertEquals(t, next.getSequenceNumber());
        }
    }

    @Test
    public void testReadAggregate_WrongIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory);
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
        fixture.getEventStore().publish(
                new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()),
                new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()));
    }

    @Test(expected = EventStoreException.class)
    public void testFixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        String identifier = UUID.randomUUID().toString();
        fixture.getEventStore().publish(
                new GenericDomainEventMessage<>("test", identifier, 0, new StubDomainEvent()),
                new GenericDomainEventMessage<>("test", identifier, 2, new StubDomainEvent()));
    }

    private class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
