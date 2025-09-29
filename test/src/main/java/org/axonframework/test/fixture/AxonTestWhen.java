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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.queryhandling.GenericQueryMessage;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Implementation of the {@link AxonTestPhase.When when-phase} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestWhen implements AxonTestPhase.When {

    private final AxonConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final RecordingQueryGateway queryGateway;
    private final MessageTypeResolver messageTypeResolver;
    private final UnitOfWorkFactory unitOfWorkFactory;

    private Message actualResult;
    private Object lastQuery;
    private CompletableFuture<?> actualQueryResult;
    private Throwable actualException;

    /**
     * Constructs an {@code AxonTestWhen} for the given parameters.
     *
     * @param configuration       The configuration which this test fixture phase is based on.
     * @param customization       Collection of customizations made for this test fixture.
     * @param commandBus          The recording {@link org.axonframework.commandhandling.CommandBus}, used to capture
     *                            and validate any commands that have been sent.
     * @param eventSink           The recording {@link org.axonframework.eventhandling.EventSink}, used to capture and
     *                            validate any events that have been sent.
     * @param queryGateway        The recording {@link org.axonframework.queryhandling.QueryGateway}, used to capture and
     *                            validate any queries that have been sent.
     * @param messageTypeResolver The message type resolver used to generate the
     *                            {@link org.axonframework.messaging.MessageType} out of command, event, or query
     *                            payloads provided to this phase.
     * @param unitOfWorkFactory   The factory of the {@link org.axonframework.messaging.unitofwork.UnitOfWork}, used to
     *                            execute every test in.
     */
    public AxonTestWhen(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryGateway queryGateway,
            @Nonnull MessageTypeResolver messageTypeResolver,
            @Nonnull UnitOfWorkFactory unitOfWorkFactory
    ) {
        this.configuration = configuration;
        this.customization = customization;
        this.commandBus = commandBus.reset();
        this.eventSink = eventSink.reset();
        this.queryGateway = queryGateway.reset();
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

    @Override
    public Query query(@Nonnull Object payload, @Nonnull Metadata metadata) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        var baseMessage = new GenericEventMessage(messageType, payload, metadata);
        var message = new GenericQueryMessage(baseMessage, ResponseTypes.instanceOf(Object.class));
        inUnitOfWorkOnInvocation(processingContext -> {
            try {
                lastQuery = message;
                actualQueryResult = queryGateway.query(message, Object.class, processingContext);
                actualException = null;
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                lastQuery = message;
                actualQueryResult = CompletableFuture.failedFuture(e);
                actualException = e;
                return CompletableFuture.failedFuture(e);
            }
        });
        return new Query();
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

    class Query implements AxonTestPhase.When.Query {

        @Override
        public AxonTestPhase.Then.Query then() {
            return new AxonTestThenQuery(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    queryGateway,
                    lastQuery,
                    actualQueryResult,
                    actualException
            );
        }
    }

    @Override
    public AxonTestPhase.When.Nothing nothing() {
        return new Nothing();
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
