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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;

/**
 * Fixture for testing Axon Framework application. The fixture can be configured to use your whole application
 * configuration or just a portion of that (single module or component). The fixture allows the execution of
 * given-when-then style.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AxonTestFixture implements AxonTestPhase.Preparing, AxonTestPhase.Executing, AxonTestPhase.Validation {

    public static final String TEST_CONTEXT = "TEST_CONTEXT";

    // configuration
    private final NewConfiguration configuration;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;

    // given
    private final AsyncUnitOfWork givenUnitOfWork;

    // when
    private final AsyncUnitOfWork whenUnitOfWork;

    // then
    private final Reporter reporter = new Reporter();
    private List<FieldFilter> fieldFilters = new ArrayList<>();
    private Throwable actualException;
    private Message<?> actualReturnValue;

    public static AxonTestFixture with(ApplicationConfigurer<?> configurer) {
        var testConfigurer = new TestApplicationConfigurer(configurer);
        var configuration = testConfigurer.build();
        return new AxonTestFixture(configuration);
    }

    public static AxonTestFixture with(TestApplicationConfigurer configurer) {
        var configuration = configurer.build();
        return new AxonTestFixture(configuration);
    }

    public AxonTestFixture(NewConfiguration configuration) {
        this.configuration = configuration;
        this.commandBus = (RecordingCommandBus) configuration.getComponent(CommandBus.class);
        this.eventSink = (RecordingEventSink) configuration.getComponent(EventSink.class);
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.givenUnitOfWork = new AsyncUnitOfWork();
        this.whenUnitOfWork = new AsyncUnitOfWork();
    }

    @Override
    public AxonTestPhase.Executing givenNoPriorActivity() {
        return this;
    }

    @Override
    public AxonTestPhase.Executing givenEvent(Object payload, MetaData metaData) {
        var messageType = messageTypeResolver.resolve(payload);
        var eventMessage = new GenericEventMessage<>(
                messageType,
                payload,
                metaData
        );
        return givenEvents(eventMessage);
    }

    public AxonTestPhase.Executing givenEvents(EventMessage<?>... events) {
        givenUnitOfWork
                .runOnInvocation(processingContext -> eventSink.publish(processingContext, TEST_CONTEXT, events))
                .runOnAfterCommit(processingContext -> eventSink.reset());
        return this;
    }

    @Override
    public AxonTestPhase.Validation when(Object payload, Map<String, ?> metaData) {
        if (!givenUnitOfWork.isCompleted()) {
            awaitCompletion(givenUnitOfWork.execute());
        }
        var messageType = messageTypeResolver.resolve(payload);
        var message = new GenericCommandMessage<>(messageType, payload, MetaData.from(metaData));
        whenUnitOfWork.onInvocation(
                processingContext -> commandBus.dispatch(message, processingContext)
                                               .whenComplete((r, e) -> {
                                                   if (e == null) {
                                                       actualReturnValue = r;
                                                   } else {
                                                       actualException = e.getCause();
                                                   }
                                               })
        );
        return this;
    }

    @Override
    public AxonTestPhase.Validation expectEvents(Object... expectedEvents) {
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
    public AxonTestPhase.Validation expectException(Matcher<?> matcher) {
        if (!whenUnitOfWork.isCompleted()) {
            awaitCompletion(whenUnitOfWork.execute());
        }
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualReturnValue.getPayload(), description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Validation expectEvents(EventMessage<?>... expectedEvents) {
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

    private void awaitCompletion(CompletableFuture<?> completion) {
        try {
            completion.join();
        } catch (Exception e) {
            actualException = e;
        }
    }
}
