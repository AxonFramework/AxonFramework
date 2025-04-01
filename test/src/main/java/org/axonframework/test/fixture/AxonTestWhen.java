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

class AxonTestWhen implements AxonTestPhase.When {

    private final NewConfiguration configuration;
    private final AxonTestFixture.Customization customization;
    private final MessageTypeResolver messageTypeResolver;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final List<AsyncUnitOfWork> unitsOfWork = new ArrayList<>();

    private Message<?> actualResult;
    private Throwable actualException;

    public AxonTestWhen(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
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
    public Command command(@Nonnull Object payload, @Nonnull MetaData metaData) {
        var messageType = messageTypeResolver.resolve(payload);
        var message = new GenericCommandMessage<>(messageType, payload, metaData);
        inUnitOfWorkOnInvocation(processingContext ->
                                         commandBus.dispatch(message, processingContext)
                                                   .whenComplete((r, e) -> {
                                                       if (e == null) {
                                                           actualResult = r;
                                                           //lastCommandException = null;
                                                       } else {
                                                           //lastCommandResult = null;
                                                           actualException = e.getCause();
                                                       }
                                                   })
        );
        return new Command();
    }

    @Override
    public Event event(@Nonnull Object payload, @Nonnull MetaData metaData) {
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
    public Event events(@Nonnull List<?>... events) {
        var messages = Arrays.stream(events)
                             .map(e -> e instanceof EventMessage<?> message
                                     ? message
                                     : toGenericEventMessage(e, MetaData.emptyInstance())
                             ).toArray(EventMessage<?>[]::new);
        return events(messages);
    }

    @Override
    public Event events(@Nonnull EventMessage<?>... messages) {
        inUnitOfWorkRunOnInvocation(processingContext -> eventSink.publish(processingContext,
                                                                           messages));
        return new Event();
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

    private void awaitCompletion(CompletableFuture<?> completion) {
        try {
            completion.join();
        } catch (Exception e) {
            this.actualResult = null;
            this.actualException = e;
        }
    }

    class Command implements AxonTestPhase.When.Command {

        @Override
        public AxonTestPhase.Then.Command then() {
            for (var unitOfWork : unitsOfWork) {
                awaitCompletion(unitOfWork.execute());
            }
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
            for (var unitOfWork : unitsOfWork) {
                awaitCompletion(unitOfWork.execute());
            }
            return new AxonTestThenEvent(
                    configuration,
                    customization,
                    commandBus,
                    eventSink,
                    actualException
            );
        }
    }
}
