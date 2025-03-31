/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.test.fixture;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.MapEntryMatcher;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.CommandValidator;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;
import static org.hamcrest.CoreMatchers.instanceOf;

abstract class AxonTestMessageThen<T extends AxonTestPhase.Then.MessageThen<T>>
        implements AxonTestPhase.Then.MessageThen<T> {

    private final Reporter reporter = new Reporter();

    private final NewConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingEventSink eventSink;

    private final CommandValidator commandValidator;
    private final Throwable lastException;

    public AxonTestMessageThen(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            Throwable lastException
    ) {
        this.configuration = configuration;
        this.customization = customization;
        this.eventSink = eventSink;
        this.lastException = lastException;
        this.commandValidator = new CommandValidator(commandBus::recordedCommands,
                                                     commandBus::reset,
                                                     new MatchAllFieldFilter(customization.fieldFilters()));
    }

    @Override
    public T events(Object... expectedEvents) {
        var publishedEvents = eventSink.recorded();

        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), lastException);
        }

        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            EventMessage<?> actualEvent = iterator.next();
            if (!verifyPayloadEquality(expectedEvent, actualEvent.getPayload())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), lastException);
            }
        }
        return self();
    }

    @Override
    public T events(EventMessage<?>... expectedEvents) {
        this.events(Stream.of(expectedEvents).map(Message::getPayload).toArray());

        var publishedEvents = eventSink.recorded();
        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (EventMessage<?> expectedEvent : expectedEvents) {
            EventMessage<?> actualEvent = iterator.next();
            if (!verifyMetaDataEquality(expectedEvent.getPayloadType(),
                                        expectedEvent.getMetaData(),
                                        actualEvent.getMetaData())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), lastException);
            }
        }
        return self();
    }

    @Override
    public T events(Matcher<? extends List<? super EventMessage<?>>> matcher) {
        var publishedEvents = eventSink.recorded();
        if (!matcher.matches(publishedEvents)) {
            final Description expectation = new StringDescription();
            matcher.describeTo(expectation);

            final Description mismatch = new StringDescription();
            matcher.describeMismatch(publishedEvents, mismatch);

            reporter.reportWrongEvent(publishedEvents, expectation, mismatch, lastException);
        }
        return self();
    }

    @Override
    public T commands(Object... expectedCommands) {
        commandValidator.assertDispatchedEqualTo(expectedCommands);
        return self();
    }

    @Override
    public T commands(CommandMessage<?>... expectedCommands) {
        commandValidator.assertDispatchedEqualTo(List.of(expectedCommands));
        return self();
    }

    @Override
    public T noCommands() {
        commandValidator.assertDispatchedEqualTo(Matchers.noCommands());
        return self();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean verifyPayloadEquality(Object expectedPayload, Object actualPayload) {
        if (Objects.equals(expectedPayload, actualPayload)) {
            return true;
        }
        if (expectedPayload != null && actualPayload == null) {
            return false;
        }
        if (expectedPayload == null) {
            return false;
        }
        if (!expectedPayload.getClass().equals(actualPayload.getClass())) {
            return false;
        }
        Matcher<Object> matcher = deepEquals(expectedPayload, new MatchAllFieldFilter(customization.fieldFilters()));
        if (!matcher.matches(actualPayload)) {
            reporter.reportDifferentPayloads(expectedPayload.getClass(), actualPayload, expectedPayload);
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean verifyMetaDataEquality(Class<?> eventType, Map<String, Object> expectedMetaData,
                                             Map<String, Object> actualMetaData) {
        MapEntryMatcher matcher = new MapEntryMatcher(expectedMetaData);
        if (!matcher.matches(actualMetaData)) {
            reporter.reportDifferentMetaData(eventType,
                                             matcher.getMissingEntries(),
                                             matcher.getAdditionalEntries());
        }
        return true;
    }

    @Override
    public AxonTestPhase.Setup and() {
        return AxonTestFixture.with(configuration, c -> customization);
    }
}
