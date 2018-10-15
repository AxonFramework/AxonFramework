/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.FixtureExecutionException;
import org.junit.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Annotated {

    private FixtureConfiguration<AnnotatedAggregate> fixture;

    @Before
    public void setUp() {
        fixture = new AggregateTestFixture<>(AnnotatedAggregate.class);
    }

    @After
    public void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            fail("A unit of work is still running");
        }
    }

    @Test
    public void testNullIdentifierIsRejected() {
        try {
            fixture.given(new MyEvent(null, 0))
                    .when(new TestCommand("test"))
                    .expectEvents(new MyEvent("test", 1))
                    .expectSuccessfulHandlerExecution();
            fail("Expected test fixture to report failure");
        } catch (AxonAssertionError error) {
            assertTrue("Expected test to fail with IncompatibleAggregateException", error.getMessage().contains("IncompatibleAggregateException"));
        }
    }

    @Test
    public void testAggregateCommandHandlersOverwrittenByCustomHandlers() {
        final AtomicBoolean invoked = new AtomicBoolean(false);
        fixture.registerCommandHandler(CreateAggregateCommand.class, commandMessage -> {
            invoked.set(true);
            return null;
        });

        fixture.given().when(new CreateAggregateCommand()).expectEvents();
        assertTrue("", invoked.get());
    }

    @Test
    public void testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.given()
                .when(new CreateAggregateCommand());
    }

    @Test(expected = FixtureExecutionException.class)
    public void testUnavailableResourcesCausesFailure() {
        fixture.given()
                .when(new CreateAggregateCommand());
    }

    @Test
    public void testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.given(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
                .when(new TestCommand("AggregateId"))
                .expectEvents(new MyEvent("AggregateId", 3))
                .expectState(Assert::assertNotNull);

        DomainEventStream events = fixture.getEventStore().readEvents("AggregateId");
        for (int t = 0; t < 3; t++) {
            assertTrue(events.hasNext());
            DomainEventMessage next = events.next();
            assertEquals("AggregateId", next.getAggregateIdentifier());
            assertEquals(t, next.getSequenceNumber());
        }
    }

    @Test(expected = FixtureExecutionException.class)
    public void testFixtureGivenCommands_ResourcesNotAvailable() {
        fixture.givenCommands(new CreateAggregateCommand("aggregateId"));
    }

    @Test
    public void testFixtureGivenCommands_ResourcesAvailable() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand("aggregateId"),
                              new TestCommand("aggregateId"),
                              new TestCommand("aggregateId"),
                              new TestCommand("aggregateId"))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4));
    }

    @Test(expected = FixtureExecutionException.class)
    public void testAggregate_InjectCustomResourceAfterCreatingAnnotatedHandler() {
        // a 'when' will cause command handlers to be registered.
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.given()
                .when(new CreateAggregateCommand("AggregateId"));
        fixture.registerInjectableResource("I am injectable");
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

    @Test
    public void testFixture_AggregateDeleted() {
        fixture.given(new MyEvent("aggregateId", 5))
                .when(new DeleteCommand("aggregateId", false))
                .expectEvents(new MyAggregateDeletedEvent(false));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        TestExecutor exec = fixture.given(new MyEvent("aggregateId", 5));
        try {
            exec.when(new DeleteCommand("aggregateId", true));
            fail("Fixture should have failed");
        } catch (AssertionError error) {
            assertTrue("Wrong message: " + error.getMessage(), error.getMessage().contains("considered deleted"));
        }
    }

    @Test
    public void testAndGiven() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand("aggregateId"))
                .andGiven(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 2));
    }

    @Test
    public void testAndGivenCommands() {
        fixture.given(new MyEvent("aggregateId", 1))
                .andGivenCommands(new TestCommand("aggregateId"))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 3));
    }

    @Test
    public void testMultipleAndGivenCommands() {
        fixture.given(new MyEvent("aggregateId", 1))
                .andGivenCommands(new TestCommand("aggregateId"))
                .andGivenCommands(new TestCommand("aggregateId"))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4));
    }

    private class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
