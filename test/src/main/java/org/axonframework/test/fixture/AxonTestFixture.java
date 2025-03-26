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
import org.axonframework.eventsourcing.CriteriaResolver;
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
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.record.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
public class AxonTestFixture implements AxonTestPhase.Given {

    public static final String TEST_CONTEXT = "TEST_CONTEXT";

    // configuration
    private final NewConfiguration configuration;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;

    private final AsyncUnitOfWork givenUnitOfWork;

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
    }

    public AxonTestPhase.Given given(Consumer<AxonTestPhase.Given> givenConsumer) {
        givenConsumer.accept(this);
        return this;
    }

    public AxonTestPhase.Given given() {
        return this;
    }

    @Override
    public AxonTestPhase.Given noPriorActivity() {
        return this;
    }

    @Override
    public AxonTestPhase.Given event(Object payload, MetaData metaData) {
        var messageType = messageTypeResolver.resolve(payload);
        var eventMessage = new GenericEventMessage<>(
                messageType,
                payload,
                metaData
        );
        return message(eventMessage);
    }

    @Override
    public AxonTestPhase.Given message(EventMessage<?>... events) {
        givenUnitOfWork
                .runOnInvocation(processingContext -> eventSink.publish(processingContext, TEST_CONTEXT, events))
                .runOnAfterCommit(processingContext -> eventSink.reset());
        return this;
    }

    @Override
    public AxonTestPhase.When when() {
        if (!givenUnitOfWork.isCompleted()) {
            awaitCompletion(givenUnitOfWork.execute());
        }
        return new When(messageTypeResolver, commandBus, eventSink);
    }

    @Override
    public AxonTestPhase.When when(Consumer<AxonTestPhase.When> whenConsumer) {
        var when = when();
        whenConsumer.accept(when);
        return when;
    }

    private void awaitCompletion(CompletableFuture<?> completion) {
        completion.join();
    }

    static class When implements AxonTestPhase.When {

        private final MessageTypeResolver messageTypeResolver;
        private final AsyncUnitOfWork whenUnitOfWork;
        private final RecordingCommandBus commandBus;
        private final RecordingEventSink eventSink;
        private Throwable actualException;
        private Message<?> actualReturnValue;

        public When(MessageTypeResolver messageTypeResolver, RecordingCommandBus commandBus,
                    RecordingEventSink eventSink) {
            this.messageTypeResolver = messageTypeResolver;
            this.commandBus = commandBus;
            this.eventSink = eventSink;
            this.whenUnitOfWork = new AsyncUnitOfWork();
        }

        @Override
        public AxonTestPhase.When command(Object payload, Map<String, ?> metaData) {
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
        public AxonTestPhase.Then then(Consumer<AxonTestPhase.Then> thenConsumer) {
            var then = then();
            thenConsumer.accept(then);
            return then;
        }

        @Override
        public AxonTestPhase.Then then() {
            if (!whenUnitOfWork.isCompleted()) {
                awaitCompletion(whenUnitOfWork.execute());
            }
            return new Then(eventSink, actualReturnValue, actualException);
        }

        private void awaitCompletion(CompletableFuture<?> completion) {
            try {
                completion.join();
            } catch (Exception e) {
                actualException = e;
            }
        }
    }

    static class Then implements AxonTestPhase.Then {

        private final Reporter reporter = new Reporter();

        // todo: support field filters
        private List<FieldFilter> fieldFilters = new ArrayList<>();

        private final RecordingEventSink eventSink;
        private final Message<?> actualReturnValue;
        private final Throwable actualException;

        public Then(
                RecordingEventSink eventSink,
                Message<?> actualReturnValue,
                Throwable actualException
        ) {
            this.eventSink = eventSink;
            this.actualException = actualException;
            this.actualReturnValue = actualReturnValue;
        }

        @Override
        public AxonTestPhase.Then events(Object... expectedEvents) {
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
        public AxonTestPhase.Then exception(Matcher<?> matcher) {
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
        public AxonTestPhase.Then events(EventMessage<?>... expectedEvents) {
            this.events(Stream.of(expectedEvents).map(Message::getPayload).toArray());

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
                reporter.reportDifferentMetaData(eventType,
                                                 matcher.getMissingEntries(),
                                                 matcher.getAdditionalEntries());
            }
            return true;
        }
    }
}
