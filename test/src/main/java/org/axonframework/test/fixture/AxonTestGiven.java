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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Implementation of the {@link AxonTestPhase.Given given-phase} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestGiven implements AxonTestPhase.Given {

    private final AxonConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Constructs a {@code AxonTestGiven} for the given parameters.
     *
     * @param configuration       The configuration which this test fixture phase is based on.
     * @param customization       Collection of customizations made for this test fixture.
     * @param commandBus          The recording {@link org.axonframework.commandhandling.CommandBus}, used to capture
     *                            and validate any commands that have been sent.
     * @param eventSink           The recording {@link org.axonframework.eventhandling.EventSink}, used to capture and
     *                            validate any events that have been sent.
     * @param messageTypeResolver The message type resolver used to generate the
     *                            {@link org.axonframework.messaging.MessageType} out of command, event, or query
     *                            payloads provided to this phase.
     * @param unitOfWorkFactory   The factory of the {@link org.axonframework.messaging.unitofwork.UnitOfWork}, used to
     *                            execute every test in.
     */
    AxonTestGiven(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull MessageTypeResolver messageTypeResolver,
            @Nonnull UnitOfWorkFactory unitOfWorkFactory
    ) {
        this.configuration = configuration;
        this.customization = customization;
        this.commandBus = commandBus;
        this.eventSink = eventSink;
        this.messageTypeResolver = messageTypeResolver;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    @Override
    public AxonTestPhase.Given noPriorActivity() {
        return this;
    }

    @Override
    public AxonTestPhase.Given event(@Nonnull Object payload, @Nonnull Metadata metadata) {
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
    public AxonTestPhase.Given events(@Nonnull List<?> events) {
        var messages = events.stream()
                             .map(e -> e instanceof EventMessage message
                                     ? message
                                     : toGenericEventMessage(e, Metadata.emptyInstance())
                             ).toArray(EventMessage[]::new);
        return events(messages);
    }

    @Override
    public AxonTestPhase.Given events(@Nonnull EventMessage... messages) {
        inUnitOfWorkOnInvocation(
                processingContext -> eventSink.publish(processingContext, messages)
        );
        return this;
    }

    private void inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        var unitOfWork = unitOfWorkFactory.create();
        unitOfWork.onInvocation(action);
        unitOfWork.execute().join();
    }

    @Override
    public AxonTestPhase.Given command(@Nonnull Object payload, @Nonnull Metadata metadata) {
        var commandMessage = toGenericCommandMessage(payload, metadata);
        return commands(commandMessage);
    }

    @Override
    public AxonTestPhase.Given commands(@Nonnull List<?> commands) {
        var messages = commands.stream()
                               .map(c -> c instanceof CommandMessage message
                                       ? message
                                       : toGenericCommandMessage(c, Metadata.emptyInstance())
                               ).toArray(CommandMessage[]::new);
        return commands(messages);
    }

    @Override
    public AxonTestPhase.Given execute(@Nonnull Function<Configuration, Void> function) {
        function.apply(configuration);
        return this;
    }

    @Override
    public AxonTestPhase.Given executeAsync(@Nonnull Function<Configuration, CompletableFuture<?>> function) {
        function.apply(configuration).join();
        return this;
    }

    private GenericCommandMessage toGenericCommandMessage(@Nonnull Object payload,
                                                          @Nonnull Metadata metadata) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        return new GenericCommandMessage(
                messageType,
                payload,
                metadata
        );
    }

    @Override
    public AxonTestPhase.Given commands(@Nonnull CommandMessage... messages) {
        for (var message : messages) {
            inUnitOfWorkOnInvocation(processingContext -> commandBus.dispatch(message, processingContext));
        }
        return this;
    }

    @Override
    public AxonTestPhase.When when() {
        return new AxonTestWhen(
                configuration,
                customization,
                commandBus,
                eventSink,
                messageTypeResolver,
                unitOfWorkFactory
        );
    }

    @Override
    public AxonTestPhase.Then.Nothing then() {
        return new AxonTestThenNothing(
                configuration,
                customization,
                commandBus,
                eventSink,
                null
        );
    }
}
