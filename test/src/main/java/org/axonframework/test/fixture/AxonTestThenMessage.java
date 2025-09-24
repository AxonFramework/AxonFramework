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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.MapStringEntryMatcher;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.CommandValidator;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;

/**
 * Abstract implementation of the {@link AxonTestPhase.Then then-phase} of the {@link AxonTestFixture}.
 *
 * @param <T> The type of {@link org.axonframework.messaging.Message} validated by this implementation.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
abstract class AxonTestThenMessage<T extends AxonTestPhase.Then.Message<T>>
        implements AxonTestPhase.Then.Message<T> {

    protected final Reporter reporter = new Reporter();

    private final AxonConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingEventSink eventSink;
    private final RecordingCommandBus commandBus;

    private final CommandValidator commandValidator;
    protected final Throwable actualException;

    /**
     * Constructs an {@code AxonTestThenMessage} for the given parameters.
     *
     * @param configuration   The configuration which this test fixture phase is based on.
     * @param customization   Collection of customizations made for this test fixture.
     * @param commandBus      The recording {@link org.axonframework.commandhandling.CommandBus}, used to capture and
     *                        validate any commands that have been sent.
     * @param eventSink       The recording {@link org.axonframework.eventhandling.EventSink}, used to capture and
     *                        validate any events that have been sent.
     * @param actualException The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenMessage(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nullable Throwable actualException
    ) {
        this.configuration = configuration;
        this.customization = customization;
        this.commandBus = commandBus;
        this.eventSink = eventSink;
        this.actualException = actualException;
        this.commandValidator = new CommandValidator(commandBus::recordedCommands,
                                                     commandBus::reset,
                                                     new MatchAllFieldFilter(customization.fieldFilters()));
    }

    @Override
    public T events(@Nonnull Object... expectedEvents) {
        var publishedEvents = eventSink.recorded();

        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<EventMessage> iterator = publishedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            EventMessage actualEvent = iterator.next();
            if (!verifyPayloadEquality(expectedEvent, actualEvent.payload())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return self();
    }

    @Override
    public T events(@Nonnull EventMessage... expectedEvents) {
        this.events(Stream.of(expectedEvents).map(org.axonframework.messaging.Message::payload).toArray());

        var publishedEvents = eventSink.recorded();
        Iterator<EventMessage> iterator = publishedEvents.iterator();
        for (EventMessage expectedEvent : expectedEvents) {
            EventMessage actualEvent = iterator.next();
            if (!verifyMetadataEquality(expectedEvent.payloadType(),
                                        expectedEvent.metadata(),
                                        actualEvent.metadata())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return self();
    }

    @Override
    public T eventsSatisfy(@Nonnull Consumer<List<EventMessage>> consumer) {
        var publishedEvents = eventSink.recorded();
        try {
            consumer.accept(publishedEvents);
        } catch (AssertionError e) {
            throw new AxonAssertionError("Events does not satisfy custom assertions", e);
        }
        return self();
    }

    @Override
    public T eventsMatch(@Nonnull Predicate<List<EventMessage>> predicate) {
        var publishedEvents = eventSink.recorded();
        var result = predicate.test(publishedEvents);
        if (!result) {
            throw new AxonAssertionError("Events does not satisfy the predicate");
        }
        return self();
    }

    @Override
    public T commands(@Nonnull Object... expectedCommands) {
        commandValidator.assertDispatchedEqualTo(expectedCommands);
        return self();
    }

    @Override
    public T commands(@Nonnull CommandMessage... expectedCommands) {
        commandValidator.assertDispatchedEqualTo(List.of(expectedCommands));
        return self();
    }

    @Override
    public T commandsSatisfy(@Nonnull Consumer<List<CommandMessage>> consumer) {
        var dispatchedCommands = commandBus.recordedCommands();
        try {
            consumer.accept(dispatchedCommands);
        } catch (AssertionError e) {
            throw new AxonAssertionError("Commands does not satisfy custom assertions", e);
        }
        return self();
    }

    @Override
    public T commandsMatch(@Nonnull Predicate<List<CommandMessage>> predicate) {
        var dispatchedCommands = commandBus.recordedCommands();
        var result = predicate.test(dispatchedCommands);
        if (!result) {
            throw new AxonAssertionError("Events does not satisfy the predicate");
        }
        return self();
    }

    @Override
    public T noCommands() {
        commandValidator.assertDispatchedMatching(Matchers.noCommands());
        return self();
    }

    @Override
    public T exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        try {
            consumer.accept(actualException);
        } catch (AssertionError e) {
            throw new AxonAssertionError("Exception does not satisfy custom assertions", e);
        }
        return self();
    }

    @Override
    public T exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        if (actualException == null) {
            throw new AxonAssertionError(
                    "Expected exception of type " + type + " with message '" + message + "' but got none");
        }
        if (!type.isInstance(actualException) || !message.equals(actualException.getMessage())) {
            throw new AxonAssertionError(
                    "Expected " + type + " with message '" + message + "' but got " + actualException);
        }
        return self();
    }

    @Override
    public T exception(@Nonnull Class<? extends Throwable> type) {
        if (actualException == null) {
            throw new AxonAssertionError(
                    "Expected exception of type " + type + " but got none");
        }
        if (!type.isInstance(actualException)) {
            throw new AxonAssertionError(
                    "Expected " + type + " but got " + actualException);
        }
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
    protected boolean verifyMetadataEquality(Class<?> eventType,
                                             Map<String, String> expectedMetadata,
                                             Map<String, String> actualMetadata) {
        MapStringEntryMatcher matcher = new MapStringEntryMatcher(expectedMetadata);
        if (!matcher.matches(actualMetadata)) {
            reporter.reportDifferentMetadata(eventType,
                                             matcher.getMissingEntries(),
                                             matcher.getAdditionalEntries());
        }
        return true;
    }

    @Override
    public AxonTestPhase.Setup and() {
        return new AxonTestFixture(configuration, customization);
    }

    @Override
    public T execute(@Nonnull Function<Configuration, Void> function) {
        function.apply(configuration);
        return self();
    }

    @Override
    public T executeAsync(@Nonnull Function<Configuration, CompletableFuture<?>> function) {
        function.apply(configuration).join();
        return self();
    }

    private T self() {
        //noinspection unchecked
        return (T) this;
    }
}
