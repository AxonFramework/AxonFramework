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

package org.axonframework.eventhandling.annotations;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlerRegistry;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotations.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotations.HandlerDefinition;
import org.axonframework.messaging.interceptors.annotations.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotations.MessageHandlingMember;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns any {@link org.axonframework.eventhandling.annotations.EventHandler} annotated bean into a
 * {@link MessageHandler} implementation. Each annotated method is subscribed as Event Handler at the
 * {@link org.axonframework.eventhandling.EventSink} for the event type specified by the parameter of that method.
 *
 * @param <T> The type of the annotated event handler.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AnnotatedEventHandlingComponent<T> implements EventHandlingComponent {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final EventHandlingComponent delegate;

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.eventhandling.annotations.EventHandler} annotated
     *                                 methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory
    ) {
        this(
                annotatedEventHandler,
                parameterResolverFactory,
                new SimpleEventHandlingComponent(),
                ClasspathHandlerDefinition.forClass(annotatedEventHandler.getClass())
        );
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.eventhandling.annotations.EventHandler} annotated
     *                                 methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param delegate                 The delegate event handling component to which the handlers will be subscribed.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull EventHandlingComponent delegate) {
        this(
                annotatedEventHandler,
                parameterResolverFactory,
                delegate,
                ClasspathHandlerDefinition.forClass(annotatedEventHandler.getClass())
        );
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a
     * {@link org.axonframework.eventhandling.EventSink} as an {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.eventhandling.annotations.EventHandler} annotated
     *                                 methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     * @param delegate                 The delegate event handling component to which the handlers will be subscribed.
     */
    @SuppressWarnings("unchecked")
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull EventHandlingComponent delegate,
                                           @Nonnull HandlerDefinition handlerDefinition) {
        this(
                annotatedEventHandler,
                delegate,
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
     *                              {@link org.axonframework.eventhandling.annotations.EventHandler} annotated methods.
     * @param delegate              The delegate event handling component to which the handlers will be subscribed.
     * @param model                 The inspector to use to find the annotated handlers on the annotatedEventHandler.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull EventHandlingComponent delegate,
                                           @Nonnull AnnotatedHandlerInspector<T> model
    ) {
        this.target = requireNonNull(annotatedEventHandler, "The Annotated Event Handler may not be null");
        this.model = requireNonNull(model, "The Annotated Handler Inspector may not be null");

        this.delegate = delegate;
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
        Class<?> payloadType = handler.payloadType();
        QualifiedName qualifiedName = handler.unwrap(MethodEventHandlerDefinition.MethodEventMessageHandlingMember.class)
                                             .map(EventHandlingMember::eventName)
                                             .map(QualifiedName::new)
                                             .orElseGet(() -> new QualifiedName(payloadType));
        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        delegate.subscribe(
                qualifiedName,
                interceptedEventHandler(qualifiedName, handler, interceptorChain)
        );
    }

    @Nonnull
    private EventHandler interceptedEventHandler(
            QualifiedName qualifiedName,
            MessageHandlingMember<? super T> handler,
            MessageHandlerInterceptorMemberChain<T> interceptorChain
    ) {
        EventHandler interceptedEventHandler = (event, ctx) ->
                interceptorChain.handle(
                        event.withConvertedPayload(handler.payloadType(), ctx.component(EventConverter.class)),
                        ctx,
                        target,
                        handler
                ).ignoreEntries().cast();

        var sequencingPolicy = handler
                .unwrap(MethodSequencingPolicyEventHandlerDefinition.SequencingPolicyEventMessageHandlingMember.class)
                .map(MethodSequencingPolicyEventHandlerDefinition.SequencingPolicyEventMessageHandlingMember::sequencingPolicy);

        return sequencingPolicy
                .map(sp -> (EventHandler) new SimpleEventHandlingComponent(sp).subscribe(qualifiedName, interceptedEventHandler))
                .orElse(interceptedEventHandler);
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
        return delegate.subscribe(name, eventHandler);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        return delegate.handle(event, context);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return Set.copyOf(delegate.supportedEvents());
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return delegate.sequenceIdentifierFor(event, context);
    }
}
