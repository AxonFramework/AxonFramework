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

package org.axonframework.test.saga;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.eventscheduler.ScheduledItem;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static org.axonframework.test.matchers.Matchers.equalTo;
import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.hamcrest.CoreMatchers.any;

/**
 * @author Allard Buijze
 * @since 1.1
 */
class FixtureExecutionResultImpl implements FixtureExecutionResult, EventListener {

    private final InMemorySagaRepository sagaRepository;
    private final StubEventScheduler eventScheduler;
    private final EventBus eventBus;
    private final RecordingCommandBus commandBus;
    private final Class<? extends AbstractAnnotatedSaga> sagaType;
    private final List<Event> publishedEvents = new ArrayList<Event>();

    FixtureExecutionResultImpl(InMemorySagaRepository sagaRepository, StubEventScheduler eventScheduler,
                               EventBus eventBus, RecordingCommandBus commandBus,
                               Class<? extends AbstractAnnotatedSaga> sagaType) {
        this.sagaRepository = sagaRepository;
        this.eventScheduler = eventScheduler;
        this.eventBus = eventBus;
        this.commandBus = commandBus;
        this.sagaType = sagaType;
    }

    @Override
    public void handle(Event event) {
        publishedEvents.add(event);
    }

    public void startRecording() {
        eventBus.subscribe(this);
        commandBus.clearCommands();
    }

    @Override
    public FixtureExecutionResult expectActiveSagas(int expected) {
        if (expected != sagaRepository.size()) {
            throw new AxonAssertionError(format("Wrong number of active sagas. Expected <%s>, got <%s>.",
                                                expected,
                                                sagaRepository.size()));
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectAssociationWith(String associationKey, Object associationValue) {
        Set<? extends AbstractAnnotatedSaga> associatedSagas =
                sagaRepository.find(sagaType, Collections.singleton(new AssociationValue(associationKey,
                                                                                         associationValue)));
        if (associatedSagas.isEmpty()) {
            throw new AxonAssertionError(format(
                    "Expected a saga to be associated with key:<%s> value:<%s>, but found <none>",
                    associationKey,
                    associationValue));
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue) {
        Set<? extends AbstractAnnotatedSaga> associatedSagas =
                sagaRepository.find(sagaType, Collections.singleton(new AssociationValue(associationKey,
                                                                                         associationValue)));
        if (!associatedSagas.isEmpty()) {
            throw new AxonAssertionError(format(
                    "Expected a saga to be associated with key:<%s> value:<%s>, but found <%s>",
                    associationKey,
                    associationValue,
                    associatedSagas.size()));
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Matcher<? extends ApplicationEvent> matcher) {
        DateTime targetTime = eventScheduler.getCurrentDateTime().plus(duration);
        return expectScheduledEvent(targetTime, matcher);
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, ApplicationEvent applicationEvent) {
        return expectScheduledEvent(duration, equalTo(applicationEvent));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Class<? extends ApplicationEvent> eventType) {
        return expectScheduledEvent(duration, any(eventType));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime,
                                                       Matcher<? extends ApplicationEvent> matcher) {

        List<ScheduledItem> schedule = eventScheduler.getScheduledItems();
        for (ScheduledItem item : schedule) {
            if (item.getScheduleTime().equals(scheduledTime) && matcher.matches(item.getEvent())) {
                return this;
            }
        }
        throw new AxonAssertionError("Did not find an event at the given schedule.");// + describe(schedule));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime, ApplicationEvent applicationEvent) {
        return expectScheduledEvent(scheduledTime, equalTo(applicationEvent));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime,
                                                       Class<? extends ApplicationEvent> eventType) {
        return expectScheduledEvent(scheduledTime, any(eventType));
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommands(Object... expected) {
        List<Object> actual = commandBus.getDispatchedCommands();
        if (actual.size() != expected.length) {
            throw new AxonAssertionError(format(
                    "Got wrong number of commands dispatched. Expected <%s>, got <%s>",
                    expected.length,
                    actual.size()));
        }
        Iterator<Object> actualIterator = actual.iterator();
        Iterator<Object> expectedIterator = Arrays.asList(expected).iterator();

        int counter = 0;
        while (actualIterator.hasNext()) {
            Object actualItem = actualIterator.next();
            Object expectedItem = expectedIterator.next();
            if (!expectedItem.equals(actualItem)) {
                throw new AxonAssertionError(format(
                        "Unexpected command at position %s (0-based). Expected <%s>, got <%s>",
                        counter,
                        expectedItem,
                        actualItem));
            }
            counter++;
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommands(Matcher<List<?>> matcher) {
        if (!matcher.matches(commandBus.getDispatchedCommands())) {
            Description expectedDescription = new StringDescription();
            Description actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(commandBus.getDispatchedCommands(), actualDescription);
            throw new AxonAssertionError(format("Incorrect dispatched commands. Expected <%s>, but got <%s>",
                                                expectedDescription, actualDescription));
        }
        return this;
    }

    private void describe(List<?> list, Description description) {
        int counter = 0;
        description.appendText("List with ");
        for (Object item : list) {
            description.appendText("<")
                       .appendText(item != null ? item.toString() : "null")
                       .appendText(">");
            if (counter == list.size() - 2) {
                description.appendText(" and ");
            } else if (counter < list.size() - 2) {
                description.appendText(", ");
            }
            counter++;
        }
    }

    @Override
    public FixtureExecutionResult expectNoScheduledEvents() {
        List<ScheduledItem> scheduledItems = eventScheduler.getScheduledItems();
        if (scheduledItems != null && !scheduledItems.isEmpty()) {
            throw new AxonAssertionError("Expected no scheduled events, got " + scheduledItems.size());
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Matcher<List<? extends Event>> matcher) {
        if (!matcher.matches(publishedEvents)) {
            StringDescription expectedDescription = new StringDescription();
            StringDescription actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(publishedEvents, actualDescription);
            throw new AxonAssertionError(format("Published events did not match.\nExpected:\n<%s>\n\nGot:\n<%s>\n",
                                                expectedDescription, actualDescription));
        }
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Event... expected) {
        expectPublishedEvents(exactSequenceOf(createEqualToMatchers(expected)));
        return this;
    }

    @SuppressWarnings({"unchecked"})
    private Matcher<? extends Event>[] createEqualToMatchers(Event[] expected) {
        List<Matcher<? extends Event>> matchers = new ArrayList<Matcher<? extends Event>>(expected.length);
        for (Event event : expected) {
            matchers.add(equalTo(event));
        }
        return matchers.toArray(new Matcher[matchers.size()]);
    }
}
