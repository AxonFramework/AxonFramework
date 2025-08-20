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

package org.axonframework.eventhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlerRegistry;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns any {@link org.axonframework.eventhandling.annotation.EventHandler} annotated bean into a
 * {@link MessageHandler} implementation. Each annotated method is subscribed as Event Handler at the
 * {@link org.axonframework.eventhandling.EventSink} for the event type specified by the parameter of that method.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @param <T> The type of the annotated event handler.
 */
public class AnnotatedEventHandlingComponent<T> implements EventHandlingComponent {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final SimpleEventHandlingComponent handlingComponent;

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler The object containing the
     *                              {@link org.axonframework.eventhandling.annotation.EventHandler} annotated methods.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler) {
        this(annotatedEventHandler, ClasspathParameterResolverFactory.forClass(annotatedEventHandler.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.eventhandling.annotation.EventHandler} annotated
     *                                 methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory) {
        this(annotatedEventHandler,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedEventHandler.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.eventhandling.annotation.EventHandler} annotated
     *                                 methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     */
    @SuppressWarnings("unchecked")
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull HandlerDefinition handlerDefinition) {
        this(
                annotatedEventHandler,
                AnnotatedHandlerInspector.inspectType((Class<T>) annotatedEventHandler.getClass(),
                                                      parameterResolverFactory,
                                                      handlerDefinition)
        );
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler The object containing the
     *                              {@link org.axonframework.eventhandling.annotation.EventHandler} annotated methods.
     * @param model                 The inspector to use to find the annotated handlers on the annotatedEventHandler.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull AnnotatedHandlerInspector<T> model) {
        this.target = requireNonNull(annotatedEventHandler, "The Annotated Event Handler may not be null");
        this.model = requireNonNull(model, "The Annotated Handler Inspector may not be null");

        this.handlingComponent = new SimpleEventHandlingComponent();
        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        model.getAllHandlers().forEach(
                (modelClass, handlers) ->
                        handlers.stream()
                                .filter(h -> h.canHandleMessageType(EventMessage.class))
                                .forEach(this::registerHandler));
    }

    private void registerHandler(MessageHandlingMember<? super T> handler) {
        QualifiedName qualifiedName = new QualifiedName(handler.payloadType()); // TODO #3098 - allow to define eventName on the handling member

        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        handlingComponent.subscribe(
                qualifiedName,
                (event, ctx) ->
                interceptorChain.handle(event, ctx, target, handler).ignoreEntries().cast()
        );
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
        return handlingComponent.subscribe(name, eventHandler);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        return handlingComponent.handle(event, context);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return Set.copyOf(handlingComponent.supportedEvents());
    }
}
