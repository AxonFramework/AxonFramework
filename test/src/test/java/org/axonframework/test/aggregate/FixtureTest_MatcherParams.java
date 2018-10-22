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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.test.AxonAssertionError;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class FixtureTest_MatcherParams {

    private FixtureConfiguration<StandardAggregate> fixture;

    @Before
    public void setUp() {
        fixture = new AggregateTestFixture<>(StandardAggregate.class);
        fixture.registerAggregateFactory(new StandardAggregate.Factory());
    }

    @Test
    public void testFirstFixture() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"))
                .expectResultMessageMatching(new DoesMatch<>())
                .expectEventsMatching(sequenceOf(matches(i -> true)));
    }

    @Test
    public void testPayloadsMatch() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"))
                .expectResultMessageMatching(new DoesMatch<>())
                .expectEventsMatching(payloadsMatching(sequenceOf(matches(i -> true))));
    }

    @Test
    public void testPayloadsMatchExact() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"))
                .expectResultMessageMatching(new DoesMatch<>())
                .expectEventsMatching(payloadsMatching(exactSequenceOf(matches(i -> true))));
    }

    @Test
    public void testPayloadsMatchPredicate() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(), fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"))
                .expectResultMessageMatching(new DoesMatch<>())
                .expectEventsMatching(payloadsMatching(predicate(ml -> !ml.isEmpty())));
    }

    @Test
    public void testFixture_UnexpectedException() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectResultMessageMatching(new DoesMatch<>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_UnexpectedReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectException(new DoesMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The command handler returned normally, but an exception was expected"));
            assertTrue(e.getMessage().contains(
                    "<anything> but returned with <null>"));
        }
    }

    @Test
    public void testFixture_WrongReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectResultMessageMatching(new DoesNotMatch<>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("<something you can never give me> but got <GenericCommandResultMessage{payload={null}"));
        }
    }

    @Test
    public void testFixture_WrongExceptionType() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectException(new DoesNotMatch());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains(
                    "<something you can never give me> but got <exception of type [StrangeCommandReceivedException]>"));
        }
    }

    @Test
    public void testFixture_ExpectedPublishedSameAsStored() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new StrangeCommand("aggregateId"))
                    .expectEvents(new DoesMatch<List<? extends EventMessage>>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue(e.getMessage().contains("The published events do not match the expected events"));
            assertTrue(e.getMessage().contains("FixtureTest_MatcherParams$DoesMatch <|> "));
            assertTrue(e.getMessage().contains("probable cause"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFixture_DispatchMetaDataInCommand() throws Exception {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MessageHandler<CommandMessage<?>> mockCommandHandler = mock(MessageHandler.class);
        fixture.registerCommandHandler(StrangeCommand.class, mockCommandHandler);
        fixture
                .given(givenEvents)
                .when(new StrangeCommand("aggregateId"), Collections.singletonMap("meta", "value"));

        final ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockCommandHandler).handle(captor.capture());
        List<CommandMessage> dispatched = captor.getAllValues();
        assertEquals(1, dispatched.size());
        assertEquals(1, dispatched.get(0).getMetaData().size());
        assertEquals("value", dispatched.get(0).getMetaData().get("meta"));
    }

    @Test
    public void testFixture_EventDoesNotMatch() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        try {
            fixture
                    .registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .when(new TestCommand("aggregateId"))
                    .expectEventsMatching(new DoesNotMatch<>());
            fail("Expected an AxonAssertionError");
        } catch (AxonAssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("something you can never give me"));
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
