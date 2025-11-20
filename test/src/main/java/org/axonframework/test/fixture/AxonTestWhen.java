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
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.test.util.EventProcessorUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Implementation of the {@link AxonTestPhase.When when-phase} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
class AxonTestWhen implements AxonTestPhase.When {

    private final AxonConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final RecordingQueryBus queryBus;
    private final MessageTypeResolver messageTypeResolver;
    private final UnitOfWorkFactory unitOfWorkFactory;

    private Message actualResult;
    private Throwable actualException;

    /**
     * Constructs an {@code AxonTestWhen} for the given parameters.
     *
     * @param configuration       The configuration which this test fixture phase is based on.
     * @param customization       Collection of customizations made for this test fixture.
     * @param commandBus          The recording {@link CommandBus}, used to capture
     *                            and validate any commands that have been sent.
     * @param eventSink           The recording {@link EventSink}, used to capture and
     *                            validate any events that have been sent.
     * @param queryBus            The recording {@link org.axonframework.messaging.queryhandling.QueryBus},
     *                            used to capture and validate any queries that have been sent.
     * @param messageTypeResolver The message type resolver used to generate the
     *                            {@link MessageType} out of command, event, or query
     *                            payloads provided to this phase.
     * @param unitOfWorkFactory   The factory of the {@link UnitOfWork}, used to
     *                            execute every test in.
     */
    public AxonTestWhen(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryBus queryBus,
            @Nonnull MessageTypeResolver messageTypeResolver,
            @Nonnull UnitOfWorkFactory unitOfWorkFactory
    ) {
        this.configuration = configuration;
        this.customization = customization;
        this.commandBus = commandBus.reset();
        this.eventSink = eventSink.reset();
        this.queryBus = queryBus.reset();
        this.messageTypeResolver = messageTypeResolver;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    @Override
    public Command command(@Nonnull Object payload, @Nonnull Metadata metadata) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        var message = new GenericCommandMessage(messageType, payload, metadata);
        inUnitOfWorkOnInvocation(processingContext ->
                                         commandBus.dispatch(message, processingContext)
                                                   .whenComplete((r, e) -> {
                                                       if (e == null) {
                                                           actualResult = r;
                                                           actualException = null;
                                                       } else {
                                                           actualResult = null;
                                                           actualException = e.getCause();
                                                       }
                                                   })
        );
        return new Command();
    }

    @Override
    public Event event(@Nonnull Object payload, @Nonnull Metadata metadata) {
        var eventMessage = toGenericEventMessage(payload, metadata);
        return events(eventMessage);
    }

    private GenericEventMessage toGenericEventMessage(Object payload, Metadata metadata) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        return new GenericEventMessage(
                messageType,
                payload,
                metadata
        );
    }

    @Override
    public Event events(@Nonnull List<?>... events) {
        var messages = Arrays.stream(events)
                             .map(e -> e instanceof EventMessage message
                                     ? message
                                     : toGenericEventMessage(e, Metadata.emptyInstance())
                             ).toArray(EventMessage[]::new);
        return events(messages);
    }

    @Override
    public Event events(@Nonnull EventMessage... messages) {
        inUnitOfWorkOnInvocation(processingContext -> eventSink.publish(processingContext, messages));
        return new Event();
    }

    private void inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        var unitOfWork = unitOfWorkFactory.create();
        unitOfWork.onInvocation(action);
        awaitCompletion(unitOfWork.execute());
    }

    private void awaitCompletion(CompletableFuture<?> completion) {
        try {
            completion.join();
        } catch (Exception e) {
            this.actualResult = null;
            this.actualException = e.getCause();
        }
    }

    class Command implements AxonTestPhase.When.Command {

        @Override
        public AxonTestPhase.Then.Command then() {
            return new AxonTestThenCommand(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    actualResult,
                    actualException
            );
        }
    }

    class Event implements AxonTestPhase.When.Event {

        @Override
        public AxonTestPhase.Then.Event then() {
            return new AxonTestThenEvent(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    actualException
            );
        }
    }

    @Override
    public AxonTestPhase.When.Query query(@Nonnull Object payload) {
        return query(payload, Duration.ofSeconds(5));
    }

    /**
     * Dispatches the given {@code payload} query to the appropriate query handler. This method automatically
     * waits for asynchronous event processors to catch up before executing the query, ensuring that the read
     * model has been updated with all previously published events.
     * <p>
     * The waiting mechanism compares each processor's current tracking token position against the latest token
     * position from the event store. Once all processors have reached or passed the head position, the query
     * is executed.
     * <p>
     * Note: Use {@code Duration.ZERO} as the timeout to skip waiting and execute the query immediately (useful
     * for synchronous event processors or when no event processing is needed).
     *
     * @param payload The query to execute.
     * @param timeout The maximum time to wait for event processors to catch up. Use {@code Duration.ZERO} to
     *                execute immediately without waiting.
     * @return The current When.Query instance, for fluent interfacing.
     */
    public AxonTestPhase.When.Query query(@Nonnull Object payload, @Nonnull Duration timeout) {
        // Wait for asynchronous event processors to catch up before querying
        if (!timeout.isZero()) {
            EventProcessorUtils.waitForEventProcessorsToCatchUp(configuration, timeout);
        }

        var messageType = messageTypeResolver.resolveOrThrow(payload);
        var queryMessage = new GenericQueryMessage(messageType, payload);

        inUnitOfWorkOnInvocation(processingContext -> {
            var responseStream = queryBus.query(queryMessage, processingContext);

            // Get the first response from the stream
            return responseStream.first()
                                 .asCompletableFuture()
                                 .thenApply(entry -> {
                                     if (entry != null) {
                                         actualResult = entry.message();
                                         actualException = null;
                                     } else {
                                         actualResult = null;
                                         actualException = new RuntimeException("No query handler found for query: " + queryMessage.payloadType());
                                     }
                                     return null;
                                 })
                                 .exceptionally(throwable -> {
                                     actualResult = null;
                                     actualException = throwable;
                                     return null;
                                 });
        });

        return new Query();
    }

    @Override
    public AxonTestPhase.When.Nothing nothing() {
        return new Nothing();
    }

    class Query implements AxonTestPhase.When.Query {

        @Override
        public AxonTestPhase.Then.Query then() {
            return new AxonTestThenQuery(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    queryBus,
                    actualResult,
                    actualException
            );
        }
    }

    class Nothing implements AxonTestPhase.When.Nothing {

        @Override
        public AxonTestPhase.Then.Nothing then() {
            return new AxonTestThenNothing(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    actualException
            );
        }
    }
}
