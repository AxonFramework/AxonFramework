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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.unitofwork.StubProcessingContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Abstract base class for tests of the {@link AnnotatedEntityModel} that provide common setup for parameter resolver
 * factory and message type resolver. In addition, it makes it easier to fire commands and events against the model.
 *
 * @param <E> The type of the entity being tested.
 * @author Mitchell Herrijgers
 */
public abstract class AbstractAnnotatedEntityModelTest<E> {

    protected final ParameterResolverFactory parameterResolverFactory = createParameterResolverFactory();
    protected final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();
    protected final AnnotatedEntityModel<E> model = getModel();
    protected E modelState = null;
    protected List<Object> publishedEvents = new LinkedList<>();

    protected abstract AnnotatedEntityModel<E> getModel();

    protected Object handleInstanceCommand(Object command) {
        CommandMessage<?> message = createCommand(command);
        try {
            return model.handleInstance(
                                message, modelState, StubProcessingContext.forMessage(message)
                        )
                        .first()
                        .asCompletableFuture()
                        .thenApply(MessageStream.Entry::message)
                        .thenApply(CommandResultMessage::getPayload)
                        .join();
        } catch (Exception e) {
            if (e instanceof CompletionException && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    protected Object handleCreateCommand(Object command) {
        CommandMessage<?> message = createCommand(command);
        try {
            return model.handleCreate(
                                message, StubProcessingContext.forMessage(message)
                        )
                        .first()
                        .asCompletableFuture()
                        .thenApply(MessageStream.Entry::message)
                        .thenApply(CommandResultMessage::getPayload)
                        .join();
        } catch (Exception e) {
            if (e instanceof CompletionException && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    protected E evolve(E entity, Object event) {
        EventMessage<?> message = new GenericEventMessage<>(new MessageType(event.getClass()), event);
        return model.evolve(entity, message, StubProcessingContext.forMessage(message));
    }

    protected <P> CommandMessage<P> createCommand(P command) {
        return new GenericCommandMessage<>(new MessageType(command.getClass()), command);
    }

    protected <P> EventMessage<P> createEvent(P event) {
        return new GenericEventMessage<>(new MessageType(event.getClass()), event);
    }

    protected ParameterResolverFactory createParameterResolverFactory() {
        var appender = new ModelEvolvingEventAppender();
        return new MultiParameterResolverFactory(
                ClasspathParameterResolverFactory.forClass(
                        getClass()),
                new SimpleResourceParameterResolverFactory(Set.of(appender))
        );
    }

    private class ModelEvolvingEventAppender implements EventAppender {

        @Override
        public void append(@Nonnull List<?> events) {
            publishedEvents.addAll(events);
            if (modelState == null) {
                return;
            }
            events.forEach(event -> {
                EventMessage<?> eventMessage;
                if (event instanceof EventMessage) {
                    eventMessage = (EventMessage<?>) event;
                } else {
                    eventMessage = createEvent(event);
                }
                model.evolve(
                        modelState, eventMessage, StubProcessingContext.forMessage(eventMessage)
                );
            });
        }
    }

    protected QualifiedName qualifiedName(Class<?> clazz) {
        return messageTypeResolver.resolveOrThrow(clazz).qualifiedName();
    }
}
