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

package org.axonframework.test;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.repository.Repository;
import org.junit.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest {

    private FixtureConfiguration fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture();
    }

    @Test
    public void testFirstFixture() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                                     fixture.getEventBus()))
                .given(new MyEvent(1))
                .when(new TestCommand())
                .expectReturnValue(Void.TYPE)
                .expectEvents(new MyEvent(2));
    }

    @Test
    public void testFixture_SetterInjection() {
        MyCommandHandler commandHandler = new MyCommandHandler();
        commandHandler.setRepository(fixture.createGenericRepository(MyAggregate.class));
        fixture.registerAnnotatedCommandHandler(commandHandler)
                .given(new MyEvent(1), new MyEvent(2))
                .when(new TestCommand())
                .expectReturnValue(Void.TYPE)
                .expectEvents(new MyEvent(3));
    }

    @Test
    public void testFixture_GivenAList() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                                      fixture.getEventBus()))
                .given(givenEvents)
                .when(new TestCommand())
                .expectEvents(new MyEvent(4))
                .expectVoidReturnType();
    }

    @Test
    public void testFixture_CommandHandlerDispatchesNonDomainEvents() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        fixture
                .registerAnnotatedCommandHandler(commandHandler)
                .given(givenEvents)
                .when(new StrangeCommand())
                .expectStoredEvents(new MyEvent(4))
                .expectPublishedEvents(new MyEvent(4), new MyApplicationEvent(commandHandler))
                .expectException(StrangeCommandReceivedException.class);
    }

    @Test
    public void testFixture_ReportWrongNumberOfEvents() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectEvents(new MyEvent(4), new MyEvent(5));
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.FixtureTest$MyEvent <|> "));
        }

    }

    @Test
    public void testFixture_ReportWrongEvents() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectEvents(new MyOtherEvent());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.FixtureTest$MyOtherEvent <|>"
                    + " org.axonframework.test.FixtureTest$MyEvent"));
        }
    }

    @Test
    public void testFixture_UnexpectedException() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand())
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_UnexpectedReturnValue() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectException(RuntimeException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The command handler returned normally, but an exception was expected"));
            assertTrue(e.getMessage().contains(
                    "<exception of type [RuntimeException]> but returned with <void return type>"));
        }
    }

    @Test
    public void testFixture_WrongReturnValue() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectReturnValue(null);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("<null return value> but got <void return type>"));
        }
    }

    @Test
    public void testFixture_WrongExceptionType() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand())
                    .expectException(RuntimeException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "<exception of type [RuntimeException]> but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_WrongEventContents() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectEvents(new MyEvent(5)) // should be 4
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."));
            assertTrue(e.getMessage().contains("Expected <5> but got <4>"));
        }
    }

    @Test
    public void testFixture_WrongEventContents_WithNullValues() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand())
                    .expectEvents(new MyEvent(null)) // should be 4
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."));
            assertTrue(e.getMessage().contains("Expected <null> but got <4>"));
        }
    }

    @Test
    public void testFixture_ExpectedPublishedSameAsStored() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createGenericRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand())
                    .expectEvents(new MyEvent(4)) // should be 4
                    .expectException(StrangeCommandReceivedException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The stored events do not match the published events."));
            assertTrue(e.getMessage().contains(" <|> org.axonframework.test.FixtureTest$MyApplicationEvent"));
        }
    }

    public static class MyAggregate extends AbstractAnnotatedAggregateRoot {

        private int lastNumber;

        public MyAggregate(UUID aggregateIdentifier) {
            super(aggregateIdentifier);
        }

        @EventHandler
        public void handleMyEvent(MyEvent event) {
            lastNumber = event.someValue;
        }

        @EventHandler
        public void handleAll(DomainEvent event) {
            // we don't care about events
        }

        public void doSomething() {
            apply(new MyEvent(lastNumber + 1));
        }
    }

    private class MyCommandHandler {

        private Repository<MyAggregate> repository;
        private EventBus eventBus;

        private MyCommandHandler(Repository<MyAggregate> repository, EventBus eventBus) {
            this.repository = repository;
            this.eventBus = eventBus;
        }

        private MyCommandHandler() {
        }

        @CommandHandler
        public void handleTestCommand(TestCommand testCommand) {
            MyAggregate aggregate = repository.get(fixture.getAggregateIdentifier(), null);
            aggregate.doSomething();
            repository.save(aggregate);
        }

        @CommandHandler
        public void handleStrangeCommand(StrangeCommand testCommand) {
            MyAggregate aggregate = repository.get(fixture.getAggregateIdentifier(), null);
            aggregate.doSomething();
            repository.save(aggregate);
            eventBus.publish(new MyApplicationEvent(this));
            throw new StrangeCommandReceivedException("Strange command received");
        }

        public void setRepository(Repository<MyAggregate> repository) {
            this.repository = repository;
        }
    }

    private class TestCommand {

    }

    private class StrangeCommand {

    }

    public static class MyEvent extends DomainEvent {

        private static final long serialVersionUID = -8646752013150772644L;
        private Integer someValue;

        public MyEvent(Integer someValue) {
            this.someValue = someValue;
        }

    }

    private class MyApplicationEvent extends ApplicationEvent {

        private static final long serialVersionUID = 8291016745540119918L;

        public MyApplicationEvent(Object source) {
            super(source);
        }
    }

    private static class StrangeCommandReceivedException extends RuntimeException {

        private static final long serialVersionUID = -486498386422064414L;

        private StrangeCommandReceivedException(String message) {
            super(message);
        }
    }

    private class MyOtherEvent extends DomainEvent {

        private static final long serialVersionUID = 7157370425417821865L;

        private MyOtherEvent() {
        }
    }
}
