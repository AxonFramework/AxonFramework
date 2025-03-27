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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.matchers.MapEntryMatcher;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;
import static org.hamcrest.CoreMatchers.*;

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
// todo: better name? CommandModelTestFixture?
public class AxonTestFixture implements AxonTestPhase.Setup {

    private final NewConfiguration configuration;
    private final Customization customization;
    private final MessageTypeResolver messageTypeResolver;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;

    private AxonTestFixture(NewConfiguration configuration, UnaryOperator<Customization> customization) {
        this.customization = customization.apply(new Customization());
        this.configuration = configuration;
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.commandBus = (RecordingCommandBus) configuration.getComponent(CommandBus.class);
        this.eventSink = (RecordingEventSink) configuration.getComponent(EventSink.class);
    }

    public static AxonTestPhase.Setup with(ApplicationConfigurer<?> configurer) {
        return with(configurer, c -> c);
    }

    public static AxonTestPhase.Setup with(ApplicationConfigurer<?> configurer,
                                           UnaryOperator<Customization> customization) {
        var testConfigurer = new TestApplicationConfigurer(configurer);
        var configuration = testConfigurer.build();
        return with(configuration, customization);
    }

    public static AxonTestPhase.Setup with(TestApplicationConfigurer configurer) {
        var configuration = configurer.build();
        return with(configuration, c -> c);
    }

    public static AxonTestPhase.Setup with(TestApplicationConfigurer configurer,
                                           UnaryOperator<Customization> customization) {
        var configuration = configurer.build();
        return with(configuration, customization);
    }

    public static AxonTestPhase.Setup with(NewConfiguration configuration) {
        return new AxonTestFixture(configuration, c -> c);
    }

    public static AxonTestPhase.Setup with(NewConfiguration configuration, UnaryOperator<Customization> customization) {
        return new AxonTestFixture(configuration, customization);
    }

    @Override
    public AxonTestPhase.Given given() {
        return new Given(configuration, customization, commandBus, eventSink, messageTypeResolver);
    }

    @Override
    public AxonTestPhase.When when() {
        return new When(configuration, customization, messageTypeResolver, commandBus, eventSink);
    }

    public record Customization(List<FieldFilter> fieldFilters) {

        public Customization() {
            this(new ArrayList<>());
        }

        public Customization registerFieldFilter(FieldFilter fieldFilter) {
            this.fieldFilters.add(fieldFilter);
            return this;
        }

        public Customization registerIgnoredField(Class<?> declaringClass, String fieldName) {
            return registerFieldFilter(new IgnoreField(declaringClass, fieldName));
        }
    }

    /**
     * Every given method invocation spawn new unit of work.
     */
    static class Given implements AxonTestPhase.Given {

        private final NewConfiguration configuration;
        private final Customization customization;
        private final RecordingCommandBus commandBus;
        private final RecordingEventSink eventSink;
        private final MessageTypeResolver messageTypeResolver;
        private final List<AsyncUnitOfWork> unitsOfWork = new ArrayList<>();

        Given(
                NewConfiguration configuration,
                Customization customization,
                RecordingCommandBus commandBus,
                RecordingEventSink eventSink,
                MessageTypeResolver messageTypeResolver
        ) {
            this.configuration = configuration;
            this.customization = customization;
            this.commandBus = commandBus;
            this.eventSink = eventSink;
            this.messageTypeResolver = messageTypeResolver;
        }

        @Override
        public AxonTestPhase.Given noPriorActivity() {
            return this;
        }

        @Override
        public AxonTestPhase.Given event(Object payload, MetaData metaData) {
            var eventMessage = toGenericEventMessage(payload, metaData);
            return events(eventMessage);
        }

        private GenericEventMessage<Object> toGenericEventMessage(Object payload, MetaData metaData) {
            var messageType = messageTypeResolver.resolve(payload);
            return new GenericEventMessage<>(
                    messageType,
                    payload,
                    metaData
            );
        }

        @Override
        public AxonTestPhase.Given events(List<?>... events) {
            var messages = Arrays.stream(events)
                                 .map(e -> e instanceof EventMessage<?> message
                                         ? message
                                         : toGenericEventMessage(e, MetaData.emptyInstance())
                                 ).toArray(EventMessage<?>[]::new);
            return events(messages);
        }

        @Override
        public AxonTestPhase.Given events(EventMessage<?>... messages) {
            inUnitOfWorkRunOnInvocation(processingContext -> eventSink.publish(processingContext,
                                                                               messages));
            return this;
        }

        private AsyncUnitOfWork inUnitOfWorkRunOnInvocation(Consumer<ProcessingContext> action) {
            var unitOfWork = new AsyncUnitOfWork();
            unitOfWork.runOnInvocation(action);
            unitsOfWork.add(unitOfWork);
            return unitOfWork;
        }

        private AsyncUnitOfWork inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
            var unitOfWork = new AsyncUnitOfWork();
            unitOfWork.onInvocation(action);
            unitsOfWork.add(unitOfWork);
            return unitOfWork;
        }

        @Override
        public AxonTestPhase.Given command(Object payload, MetaData metaData) {
            var messageType = messageTypeResolver.resolve(payload);
            var commandMessage = new GenericCommandMessage<>(
                    messageType,
                    payload,
                    metaData
            );
            return commands(commandMessage);
        }

        @Override
        public AxonTestPhase.Given commands(CommandMessage<?>... messages) {
            inUnitOfWorkOnInvocation(processingContext -> {
                CompletableFuture<? extends Message<?>> dispatchOneByOneFuture = CompletableFuture.completedFuture(null);
                for (var message : messages) {
                    var dispatchFuture = commandBus.dispatch(message, processingContext);
                    dispatchOneByOneFuture = dispatchOneByOneFuture.thenCompose(m -> dispatchFuture);
                }
                return dispatchOneByOneFuture;
            });
            return this;
        }

        @Override
        public AxonTestPhase.When when() {
            // todo: prevent double when!
            for (var unitOfWork : unitsOfWork) {
                awaitCompletion(unitOfWork.execute());
            }
            return new When(configuration, customization, messageTypeResolver, commandBus, eventSink);
        }

        private void awaitCompletion(CompletableFuture<?> completion) {
            completion.join();
        }
    }


    static class When implements AxonTestPhase.When {

        private final NewConfiguration configuration;
        private final Customization customization;
        private final MessageTypeResolver messageTypeResolver;
        private final RecordingCommandBus commandBus;
        private final RecordingEventSink eventSink;
        private final List<AsyncUnitOfWork> unitsOfWork = new ArrayList<>();

        private Message<?> lastCommandResult;
        private Throwable lastCommandException;

        public When(
                NewConfiguration configuration,
                Customization customization,
                MessageTypeResolver messageTypeResolver,
                RecordingCommandBus commandBus,
                RecordingEventSink eventSink
        ) {
            this.configuration = configuration;
            this.customization = customization;
            this.messageTypeResolver = messageTypeResolver;
            this.commandBus = commandBus.reset();
            this.eventSink = eventSink.reset();
        }

        @Override
        public AxonTestPhase.When command(Object payload, MetaData metaData) {
            var messageType = messageTypeResolver.resolve(payload);
            var message = new GenericCommandMessage<>(messageType, payload, metaData);
            inUnitOfWorkOnInvocation(processingContext ->
                                             commandBus.dispatch(message, processingContext)
                                                       .whenComplete((r, e) -> {
                                                           if (e == null) {
                                                               lastCommandResult = r;
                                                           } else {
                                                               lastCommandException = e.getCause();
                                                           }
                                                       })
            );
            return this;
        }

        @Override
        public AxonTestPhase.When event(Object payload) {
            return AxonTestPhase.When.super.event(payload);
        }

        @Override
        public AxonTestPhase.When event(Object payload, MetaData metaData) {
            var eventMessage = toGenericEventMessage(payload, metaData);
            return events(eventMessage);
        }

        private GenericEventMessage<Object> toGenericEventMessage(Object payload, MetaData metaData) {
            var messageType = messageTypeResolver.resolve(payload);
            return new GenericEventMessage<>(
                    messageType,
                    payload,
                    metaData
            );
        }

        @Override
        public AxonTestPhase.When events(List<?>... events) {
            var messages = Arrays.stream(events)
                                 .map(e -> e instanceof EventMessage<?> message
                                         ? message
                                         : toGenericEventMessage(e, MetaData.emptyInstance())
                                 ).toArray(EventMessage<?>[]::new);
            return events(messages);
        }

        @Override
        public AxonTestPhase.When events(EventMessage<?>... messages) {
            inUnitOfWorkRunOnInvocation(processingContext -> eventSink.publish(processingContext,
                                                                               messages));
            return this;
        }

        private AsyncUnitOfWork inUnitOfWorkRunOnInvocation(Consumer<ProcessingContext> action) {
            var unitOfWork = new AsyncUnitOfWork();
            unitOfWork.runOnInvocation(action);
            unitsOfWork.add(unitOfWork);
            return unitOfWork;
        }

        private AsyncUnitOfWork inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
            var unitOfWork = new AsyncUnitOfWork();
            unitOfWork.onInvocation(action);
            unitsOfWork.add(unitOfWork);
            return unitOfWork;
        }


        @Override
        public AxonTestPhase.Then then() {
            // todo: prevent double then!
            for (var unitOfWork : unitsOfWork) {
                awaitCompletion(unitOfWork.execute());
            }
            return new Then(
                    configuration,
                    customization,
                    messageTypeResolver,
                    commandBus,
                    eventSink,
                    lastCommandResult, lastCommandException);
        }

        private void awaitCompletion(CompletableFuture<?> completion) {
            try {
                completion.join();
            } catch (Exception e) {
                lastCommandException = e;
            }
        }
    }

    static class Then implements AxonTestPhase.Then {

        private final Reporter reporter = new Reporter();

        private final NewConfiguration configuration;
        private final Customization customization;
        private final MessageTypeResolver messageTypeResolver;
        private final RecordingCommandBus commandBus;
        private final RecordingEventSink eventSink;
        private final Message<?> actualReturnValue;
        private final Throwable actualException;

        public Then(
                NewConfiguration configuration,
                Customization customization,
                MessageTypeResolver messageTypeResolver,
                RecordingCommandBus commandBus,
                RecordingEventSink eventSink,
                Message<?> actualReturnValue,
                Throwable actualException
        ) {
            this.configuration = configuration;
            this.customization = customization;
            this.messageTypeResolver = messageTypeResolver;
            this.commandBus = commandBus;
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

        @Override
        public AxonTestPhase.Then events(Matcher<? extends List<? super EventMessage<?>>> matcher) {
            var publishedEvents = eventSink.recorded();
            if (!matcher.matches(publishedEvents)) {
                final Description expectation = new StringDescription();
                matcher.describeTo(expectation);

                final Description mismatch = new StringDescription();
                matcher.describeMismatch(publishedEvents, mismatch);

                reporter.reportWrongEvent(publishedEvents, expectation, mismatch, actualException);
            }
            return this;
        }

        @Override
        public AxonTestPhase.Then commands(Object... expectedCommands) {
            var publishedCommands = commandBus.recordedCommands();

            throw new RuntimeException("Not implemented yet");
        }

        @Override
        public AxonTestPhase.Then commands(CommandMessage<?>... expectedCommands) {
            throw new RuntimeException("Not implemented yet");
        }

        @Override
        public AxonTestPhase.Then success() {
            return resultMessage(anything());
        }

        @Override
        public AxonTestPhase.Then resultMessage(Matcher<? super CommandResultMessage<?>> matcher) {
            if (matcher == null) {
                return resultMessage(nullValue());
            }
            StringDescription expectedDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            if (actualException != null) {
                reporter.reportUnexpectedException(actualException, expectedDescription);
            } else if (!matcher.matches(actualReturnValue)) {
                reporter.reportWrongResult(actualReturnValue, expectedDescription);
            }
            return this;
        }

        @Override
        public AxonTestPhase.Then resultMessagePayload(Object expectedPayload) {
            StringDescription expectedDescription = new StringDescription();
            StringDescription actualDescription = new StringDescription();
            PayloadMatcher<CommandResultMessage<?>> expectedMatcher =
                    new PayloadMatcher<>(CoreMatchers.equalTo(expectedPayload));
            expectedMatcher.describeTo(expectedDescription);
            if (actualException != null) {
                reporter.reportUnexpectedException(actualException, expectedDescription);
            } else if (!verifyPayloadEquality(expectedPayload, actualReturnValue.getPayload())) {
                PayloadMatcher<CommandResultMessage<?>> actualMatcher =
                        new PayloadMatcher<>(CoreMatchers.equalTo(actualReturnValue.getPayload()));
                actualMatcher.describeTo(actualDescription);
                reporter.reportWrongResult(actualDescription, expectedDescription);
            }
            return this;
        }

        @Override
        public AxonTestPhase.Then resultMessagePayloadMatching(Matcher<?> matcher) {
            if (matcher == null) {
                return resultMessagePayloadMatching(nullValue());
            }
            StringDescription expectedDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            if (actualException != null) {
                reporter.reportUnexpectedException(actualException, expectedDescription);
            } else if (!matcher.matches(actualReturnValue.getPayload())) {
                reporter.reportWrongResult(actualReturnValue.getPayload(), expectedDescription);
            }
            return this;
        }

        @Override
        public AxonTestPhase.Then exception(Class<? extends Throwable> expectedException) {
            return exception(instanceOf(expectedException));
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
            Matcher<Object> matcher = deepEquals(expectedPayload, new MatchAllFieldFilter(customization.fieldFilters));
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

        @Override
        public AxonTestPhase.Setup and() {
            return AxonTestFixture.with(configuration, c -> customization);
        }
    }
}
