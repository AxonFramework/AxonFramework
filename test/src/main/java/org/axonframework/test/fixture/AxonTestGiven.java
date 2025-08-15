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
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

class AxonTestGiven implements AxonTestPhase.Given {

    private final Configuration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;
    private final UnitOfWorkFactory unitOfWorkFactory;

    AxonTestGiven(
            Configuration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            MessageTypeResolver messageTypeResolver,
            UnitOfWorkFactory unitOfWorkFactory
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
    public AxonTestPhase.Given event(@Nonnull Object payload, @Nonnull MetaData metaData) {
        var eventMessage = toGenericEventMessage(payload, metaData);
        return events(eventMessage);
    }

    private GenericEventMessage<Object> toGenericEventMessage(Object payload, MetaData metaData) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        return new GenericEventMessage<>(
                messageType,
                payload,
                metaData
        );
    }

    @Override
    public AxonTestPhase.Given events(@Nonnull List<?> events) {
        var messages = events.stream()
                             .map(e -> e instanceof EventMessage<?> message
                                     ? message
                                     : toGenericEventMessage(e, MetaData.emptyInstance())
                             ).toArray(EventMessage<?>[]::new);
        return events(messages);
    }

    @Override
    public AxonTestPhase.Given events(@Nonnull EventMessage<?>... messages) {
        inUnitOfWorkRunOnInvocation(processingContext -> eventSink.publish(processingContext,
                                                                           messages));
        return this;
    }

    private void inUnitOfWorkRunOnInvocation(Consumer<ProcessingContext> action) {
        var unitOfWork = unitOfWorkFactory.create();
        unitOfWork.runOnInvocation(action);
        unitOfWork.execute().join();
    }

    private void inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        var unitOfWork = unitOfWorkFactory.create();
        unitOfWork.onInvocation(action);
        unitOfWork.execute().join();
    }

    @Override
    public AxonTestPhase.Given command(@Nonnull Object payload, @Nonnull MetaData metaData) {
        var commandMessage = toGenericCommandMessage(payload, metaData);
        return commands(commandMessage);
    }

    @Override
    public AxonTestPhase.Given commands(@Nonnull List<?> commands) {
        var messages = commands.stream()
                               .map(c -> c instanceof CommandMessage<?> message
                                       ? message
                                       : toGenericCommandMessage(c, MetaData.emptyInstance())
                               ).toArray(CommandMessage<?>[]::new);
        return commands(messages);
    }

    private GenericCommandMessage<Object> toGenericCommandMessage(@Nonnull Object payload,
                                                                  @Nonnull MetaData metaData
    ) {
        var messageType = messageTypeResolver.resolveOrThrow(payload);
        return new GenericCommandMessage<>(
                messageType,
                payload,
                metaData
        );
    }

    @Override
    public AxonTestPhase.Given commands(@Nonnull CommandMessage<?>... messages) {
        for (var message : messages) {
            inUnitOfWorkOnInvocation(processingContext -> commandBus.dispatch(message, processingContext));
        }
        return this;
    }

    @Override
    public AxonTestPhase.When when() {
        return new AxonTestWhen(configuration, customization, messageTypeResolver, commandBus, eventSink, unitOfWorkFactory);
    }
}
