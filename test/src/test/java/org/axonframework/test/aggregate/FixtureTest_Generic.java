/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.IncompatibleAggregateException;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class FixtureTest_Generic {

    private FixtureConfiguration<StandardAggregate> fixture;
    private AggregateFactory<StandardAggregate> mockAggregateFactory;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(StandardAggregate.class);
        fixture.setReportIllegalStateChange(false);
        mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.getAggregateType()).thenReturn(StandardAggregate.class);
        when(mockAggregateFactory.createAggregateRoot(isA(String.class), isA(DomainEventMessage.class)))
                .thenReturn(new StandardAggregate("id1"));
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            fail("Test failed to close Unit of Work!!");
            CurrentUnitOfWork.get().rollback();
        }

    }

    @Test
    void configureCustomAggregateFactory() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));

        fixture.given(new MyEvent("id1", 1))
                .when(new TestCommand("id1"));

        verify(mockAggregateFactory).createAggregateRoot(eq("id1"), isA(DomainEventMessage.class));
    }

    @Test
    void configurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGiven() {
        assertThrows(IncompatibleAggregateException.class, () -> fixture.given(new MyEvent("id1", 1)));
    }

    @Test
    void configurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGetRepository() {
        assertThrows(IncompatibleAggregateException.class, fixture::getRepository);
    }

    @Test
    void aggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.givenNoPriorActivity()
                .when(new CreateAggregateCommand());
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void storingExistingAggregateGeneratesException() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        fixture.given(new MyEvent("aggregateId", 1))
               .when(new CreateAggregateCommand("aggregateId"))
               .expectException(EventStoreException.class)
               .expectNoEvents();
    }

    @Test
    void injectResources_CommandHandlerAlreadyRegistered() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        assertThrows(FixtureExecutionException.class, () -> fixture.registerInjectableResource("I am injectable"));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void aggregateIdentifier_IdentifierAutomaticallyDeducted() {
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
    void readAggregate_WrongIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()));
        TestExecutor exec = fixture.given(new MyEvent("AggregateId", 1));

        AssertionError error = assertThrows(AssertionError.class, () -> exec.when(new TestCommand("OtherIdentifier")));
        assertTrue(error.getMessage().contains("OtherIdentifier"), "Wrong message. Was: " + error.getMessage());
        assertTrue(error.getMessage().contains("AggregateId"), "Wrong message. Was: " + error.getMessage());
    }

    @Test
    void fixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        assertThrows(EventStoreException.class, () ->
                fixture.getEventStore().publish(
                        new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()),
                        new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()))
        );
    }

    @Test
    void fixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        String identifier = UUID.randomUUID().toString();
        assertThrows(EventStoreException.class, () ->
                fixture.getEventStore().publish(
                        new GenericDomainEventMessage<>("test", identifier, 0, new StubDomainEvent()),
                        new GenericDomainEventMessage<>("test", identifier, 2, new StubDomainEvent()))
        );
    }

    private class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
