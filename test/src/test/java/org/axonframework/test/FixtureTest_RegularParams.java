/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.repository.AggregateNotFoundException;
import org.hamcrest.core.IsNull;
import org.junit.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class FixtureTest_RegularParams {

    private FixtureConfiguration fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture();
    }

    @Test
    public void testFixture_NoEventsInStore() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                     fixture.getEventBus()))
               .given()
               .when(new TestCommand(fixture.getAggregateIdentifier()))
               .expectException(AggregateNotFoundException.class);
    }

    @Test
    public void testFirstFixture() {
        ResultValidator validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                      fixture.getEventBus()))
                .given(new MyEvent(1))
                .when(new TestCommand(fixture.getAggregateIdentifier()));
        validator.expectReturnValue(null);
        validator.expectEvents(new MyEvent(2));
    }

    @Test
    public void testFixture_SetterInjection() {
        MyCommandHandler commandHandler = new MyCommandHandler();
        commandHandler.setRepository(fixture.createRepository(MyAggregate.class));
        fixture.registerAnnotatedCommandHandler(commandHandler)
               .given(new MyEvent(1), new MyEvent(2))
               .when(new TestCommand(fixture.getAggregateIdentifier()))
               .expectReturnValue(IsNull.nullValue())
               .expectEvents(new MyEvent(3));
    }

    @Test
    public void testFixture_GivenAList() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                      fixture.getEventBus()))
                .given(givenEvents)
                .when(new TestCommand(fixture.getAggregateIdentifier()))
                .expectEvents(new MyEvent(4))
                .expectVoidReturnType();
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_ExplicitValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        try {
            fixture
                    .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                          fixture.getEventBus()))
                    .given(givenEvents)
                    .when(new IllegalStateChangeCommand(fixture.getAggregateIdentifier(), 5));
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains(".lastNumber\""));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<5>"));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<4>"));
        }
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_NullValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        try {
            fixture
                    .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                          fixture.getEventBus()))
                    .given(givenEvents)
                    .when(new IllegalStateChangeCommand(fixture.getAggregateIdentifier(), null));
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains(".lastNumber\""));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<null>"));
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("<4>"));
        }
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_Ignored() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        fixture.setReportIllegalStateChange(false);
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                      fixture.getEventBus()))
                .given(givenEvents)
                .when(new IllegalStateChangeCommand(fixture.getAggregateIdentifier(), null));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_AggregateGeneratesIdentifier() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                     fixture.getEventBus()))
               .given()
               .when(new CreateAggregateCommand(null));
    }

    @Test
    public void testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                     fixture.getEventBus()))
               .given(new MyEvent(5))
               .when(new DeleteCommand(fixture.getAggregateIdentifier()))
               .expectEvents(new MyAggregateDeletedEvent());
    }

    @Test
    public void testFixtureGivenCommands() {
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                      fixture.getEventBus()))
                .givenCommands(new CreateAggregateCommand(fixture.getAggregateIdentifier()),
                               new TestCommand(fixture.getAggregateIdentifier()),
                               new TestCommand(fixture.getAggregateIdentifier()),
                               new TestCommand(fixture.getAggregateIdentifier()))
                .when(new TestCommand(fixture.getAggregateIdentifier()))
                .expectEvents(new MyEvent(4));
    }

    @Test
    public void testFixture_CommandHandlerDispatchesNonDomainEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        // the domain events are part of the transaction, but the command handler directly dispatches an application
        // event to the event bus. This event dispatched anyway. The
        fixture
                .registerAnnotatedCommandHandler(commandHandler)
                .given(givenEvents)
                .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                .expectStoredEvents()
                .expectPublishedEvents(new MyApplicationEvent())
                .expectException(StrangeCommandReceivedException.class);
    }

    @Test
    public void testFixture_ReportWrongNumberOfEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new MyEvent(4), new MyEvent(5));
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.MyEvent <|> "));
        }
    }

    @Test
    public void testFixture_ReportWrongEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new MyOtherEvent());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("org.axonframework.test.MyOtherEvent <|>"
                                                       + " org.axonframework.test.MyEvent"));
        }
    }

    @Test
    public void testFixture_UnexpectedException() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_UnexpectedReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectException(RuntimeException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The command handler returned normally, but an exception was expected"));
            assertTrue(e.getMessage().contains(
                    "<an instance of java.lang.RuntimeException> but returned with <null>"));
        }
    }

    @Test
    public void testFixture_WrongReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                   .given(givenEvents)
                   .when(new TestCommand(fixture.getAggregateIdentifier()))
                   .expectReturnValue("some");
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("<\"some\"> but got <null>"));
        }
    }

    @Test
    public void testFixture_WrongExceptionType() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                   .given(givenEvents)
                   .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                   .expectException(IOException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "<an instance of java.io.IOException> but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_WrongEventContents() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
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
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new MyEvent(null)) // should be 4
                    .expectVoidReturnType();
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."));
            assertTrue(e.getMessage().contains("Expected <<null>> but got <4>"));
        }
    }

    @Test
    public void testFixture_ExpectedPublishedSameAsStored() {
        List<?> givenEvents = Arrays.asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new MyEvent(4)) // should be 4
                    .expectException(StrangeCommandReceivedException.class);
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The stored events do not match the published events."));
            assertTrue(e.getMessage().contains(" <|> org.axonframework.test.MyApplicationEvent"));
            assertTrue(e.getMessage().contains("probable cause"));
        }
    }
}
