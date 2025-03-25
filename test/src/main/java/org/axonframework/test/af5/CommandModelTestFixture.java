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

package org.axonframework.test.af5;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.MapEntryMatcher;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class CommandModelTestFixture implements CommandModelTest.Executor, CommandModelTest.ResultValidator {

    public static final String TEST_CONTEXT = "TEST_CONTEXT";

    // configuration
    private final NewConfiguration configuration;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;
    private List<FieldFilter> fieldFilters = new ArrayList<>();

    // given
    private final AsyncUnitOfWork givenUnitOfWork;

    // when
    private final AsyncUnitOfWork whenUnitOfWork;

    // then

    private final Reporter reporter = new Reporter();
    private Throwable actualException;

    public static CommandModelTestFixture with(ApplicationConfigurer<?> configurer) {
        var testConfigurer = new TestApplicationConfigurer(configurer);
        var configuration = testConfigurer.build();
        return new CommandModelTestFixture(configuration);
    }

    public static CommandModelTestFixture with(TestApplicationConfigurer configurer) {
        var configuration = configurer.build();
        return new CommandModelTestFixture(configuration);
    }

    public CommandModelTestFixture(NewConfiguration configuration) {
        this.configuration = configuration;
        this.commandBus = (RecordingCommandBus) configuration.getComponent(CommandBus.class);
        this.eventSink = (RecordingEventSink) configuration.getComponent(EventSink.class);
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.givenUnitOfWork = new AsyncUnitOfWork();
        this.whenUnitOfWork = new AsyncUnitOfWork();
    }

    public CommandModelTest.Executor givenNoPriorActivity() {
        return this;
    }

    public CommandModelTest.Executor givenEvent(Object payload) {
        return givenEvent(payload, MetaData.emptyInstance());
    }

    public CommandModelTest.Executor givenEvent(Object payload, Map<String, ?> metaData) {
        return givenEvent(payload, MetaData.from(metaData));
    }

    public CommandModelTest.Executor givenEvent(Object payload, MetaData metaData) {
        var messageType = messageTypeResolver.resolve(payload);
        var eventMessage = new GenericEventMessage<>(
                messageType,
                payload,
                metaData
        );
        return givenEvents(eventMessage);
    }

    public CommandModelTest.Executor givenEvents(EventMessage<?>... events) {
        givenUnitOfWork
                .runOnInvocation(processingContext -> eventSink.publish(processingContext, TEST_CONTEXT, events))
                .runOnAfterCommit(processingContext -> eventSink.reset());
        return this;
    }

    @Override
    public CommandModelTest.ResultValidator when(Object payload, Map<String, ?> metaData) {
        if (!givenUnitOfWork.isCompleted()) {
            awaitCompletion(givenUnitOfWork.execute());
        }
        var messageType = messageTypeResolver.resolve(payload);
        var message = new GenericCommandMessage<>(messageType, payload, MetaData.from(metaData));
        // todo: handle exception!
        whenUnitOfWork.runOnInvocation(
                processingContext -> commandBus.dispatch(message, processingContext)
                                               .exceptionally(e -> {
                                                   actualException = e;
                                                   return null;
                                               })
//                                               .exceptionallyCompose(e -> {
//                                                   actualException = e;
//                                                   return CompletableFuture.completedFuture(null);
//                                               })
        );
        return this;
    }

    @Override
    public CommandModelTest.ResultValidator expectEvents(Object... expectedEvents) {
        if (!whenUnitOfWork.isCompleted()) {
            awaitCompletion(whenUnitOfWork.execute());
        }
        var publishedEvents = eventSink.recorded();

        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            EventMessage<?> actualEvent = iterator.next();
            if (!verifyPayloadEquality(expectedEvent, actualEvent.getPayload())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public CommandModelTest.ResultValidator expectException(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException == null) {
            // todo: implement!
//            reporter.reportUnexpectedReturnValue(actualReturnValue.getPayload(), description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public CommandModelTest.ResultValidator expectEvents(EventMessage<?>... expectedEvents) {
        this.expectEvents(Stream.of(expectedEvents).map(Message::getPayload).toArray());

        var publishedEvents = eventSink.recorded();
        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (EventMessage<?> expectedEvent : expectedEvents) {
            EventMessage<?> actualEvent = iterator.next();
            if (!verifyMetaDataEquality(expectedEvent.getPayloadType(),
                                        expectedEvent.getMetaData(),
                                        actualEvent.getMetaData())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean verifyPayloadEquality(Object expectedPayload, Object actualPayload) {
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
        Matcher<Object> matcher = deepEquals(expectedPayload, new MatchAllFieldFilter(fieldFilters));
        if (!matcher.matches(actualPayload)) {
            reporter.reportDifferentPayloads(expectedPayload.getClass(), actualPayload, expectedPayload);
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean verifyMetaDataEquality(Class<?> eventType, Map<String, Object> expectedMetaData,
                                           Map<String, Object> actualMetaData) {
        MapEntryMatcher matcher = new MapEntryMatcher(expectedMetaData);
        if (!matcher.matches(actualMetaData)) {
            reporter.reportDifferentMetaData(eventType, matcher.getMissingEntries(), matcher.getAdditionalEntries());
        }
        return true;
    }

    private static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(completion.isCompletedExceptionally(),
                                                () -> completion.exceptionNow().toString()));
        return completion.join();
    }
}
