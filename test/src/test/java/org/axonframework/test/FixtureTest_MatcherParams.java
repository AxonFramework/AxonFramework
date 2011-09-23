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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;

import java.util.Arrays;
import java.util.List;

import static org.axonframework.test.matchers.Matchers.sequenceOf;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class FixtureTest_MatcherParams {

    private FixtureConfiguration fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture();
    }

    @Test
    public void testFirstFixture() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                                     fixture.getEventBus()))
               .given(new MyEvent(1))
               .when(new TestCommand(fixture.getAggregateIdentifier()))
               .expectReturnValue(new DoesMatch())
               .expectEvents(sequenceOf(new DoesMatch<Event>()));
    }

    @Test
    public void testFixture_UnexpectedException() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                    .expectReturnValue(new DoesMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_UnexpectedReturnValue() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectException(new DoesMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The command handler returned normally, but an exception was expected"));
            assertTrue(e.getMessage().contains(
                    "<anything> but returned with <void>"));
        }
    }

    @Test
    public void testFixture_WrongReturnValue() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                   .given(givenEvents)
                   .when(new TestCommand(fixture.getAggregateIdentifier()))
                   .expectReturnValue(new DoesNotMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("<something you can never give me> but got <void>"));
        }
    }

    @Test
    public void testFixture_WrongExceptionType() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                   .given(givenEvents)
                   .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                   .expectException(new DoesNotMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "<something you can never give me> but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_ExpectedPublishedSameAsStored() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new DoesMatch<List<? extends Event>>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The stored events do not match the published events."));
            assertTrue(e.getMessage().contains(" <|> org.axonframework.test.MyApplicationEvent"));
            assertTrue(e.getMessage().contains("probable cause"));
        }
    }

    @Test
    public void testFixture_EventDoesNotMatch() {
        List<DomainEvent> givenEvents = Arrays.<DomainEvent>asList(new MyEvent(1), new MyEvent(2), new MyEvent(3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.createRepository(MyAggregate.class),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand(fixture.getAggregateIdentifier()))
                    .expectEvents(new DoesNotMatch<List<? extends Event>>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("something you can never give me"));
        }
    }

    private static class DoesMatch<T> extends BaseMatcher<T> {

        @Override
        public boolean matches(Object o) {
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("anything");
        }
    }

    private static class DoesNotMatch<T> extends BaseMatcher<T> {

        @Override
        public boolean matches(Object o) {
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("something you can never give me");
        }
    }
}
