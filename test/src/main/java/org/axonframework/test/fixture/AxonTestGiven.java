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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every given method invocation spawns a new unit of work.
 */
class AxonTestGiven implements AxonTestPhase.Given {

    private final NewConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;
    private final List<AsyncUnitOfWork> unitsOfWork = new ArrayList<>();

    AxonTestGiven(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
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
        for (var unitOfWork : unitsOfWork) {
            awaitCompletion(unitOfWork.execute());
        }
        return new AxonTestWhen(configuration, customization, messageTypeResolver, commandBus, eventSink);
    }

    private void awaitCompletion(CompletableFuture<?> completion) {
        completion.join();
    }
}
