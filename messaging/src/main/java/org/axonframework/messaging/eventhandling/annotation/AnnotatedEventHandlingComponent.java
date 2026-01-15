/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandler;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.HandlerAttributes;
import org.axonframework.messaging.core.annotation.HandlerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventHandlerRegistry;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetHandler;
import org.axonframework.messaging.eventhandling.replay.ResetHandlerRegistry;

import java.util.Collection;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns any {@link org.axonframework.messaging.eventhandling.annotation.EventHandler @EventHandler}
 * annotated bean into a {@link MessageHandler} implementation. Each annotated method is subscribed as Event Handler at
 * the {@link EventSink} for the event type specified by the parameter of that method.
 * <p>
 * Additionally, methods annotated with
 * {@link org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler @ResetHandler} are registered to
 * handle {@link ResetContext} messages during event processor reset operations.
 *
 * @param <T> The type of the annotated event handler.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AnnotatedEventHandlingComponent<T> implements EventHandlingComponent {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;
    private final EventHandlingComponent delegate;

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a {@link EventSink} as an
     * {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.messaging.eventhandling.annotation.EventHandler}
     *                                 annotated methods.
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
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a {@link EventSink} as an
     * {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.messaging.eventhandling.annotation.EventHandler}
     *                                 annotated methods.
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
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a {@link EventSink} as an
     * {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.messaging.eventhandling.annotation.EventHandler}
     *                                 annotated methods.
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
                                                      handlerDefinition),
                new AnnotationMessageTypeResolver()
        );
    }

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to a {@link EventSink} as an
     * {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler The object containing the
     *                              {@link org.axonframework.messaging.eventhandling.annotation.EventHandler} annotated
     *                              methods.
     * @param delegate              The delegate event handling component to which the handlers will be subscribed.
     * @param model                 The inspector to use to find the annotated handlers on the annotatedEventHandler.
     */
    private AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                            @Nonnull EventHandlingComponent delegate,
                                            @Nonnull AnnotatedHandlerInspector<T> model,
                                           @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        this.target = requireNonNull(annotatedEventHandler, "The Annotated Event Handler may not be null");
        this.model = requireNonNull(model, "The Annotated Handler Inspector may not be null");
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");
        this.delegate = delegate;

        initializeEventHandlersBasedOnModel();
        initializeResetHandlersBasedOnModel();
    }

    // region [EventHandlers]
    private void initializeEventHandlersBasedOnModel() {
        model.getUniqueHandlers(target.getClass(), EventMessage.class).forEach(this::registerEventHandler);
    }

    private void registerEventHandler(MessageHandlingMember<? super T> handler) {
        Class<?> payloadType = handler.payloadType();
        QualifiedName qualifiedName = handler.unwrap(MethodEventHandlerDefinition.MethodEventMessageHandlingMember.class)
                                             .map(EventHandlingMember::eventName)
                                             .map(QualifiedName::new)
                                             .orElseGet(() -> messageTypeResolver.resolveOrThrow(payloadType).qualifiedName());
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
                .map(sp -> (EventHandler) new SimpleEventHandlingComponent(sp).subscribe(qualifiedName,
                                                                                         interceptedEventHandler))
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
    // endregion

    // region [ResetHandlers]
    private void initializeResetHandlersBasedOnModel() {
        model.getUniqueHandlers(target.getClass(), ResetContext.class).forEach(this::registerResetHandler);
    }

    private void registerResetHandler(MessageHandlingMember<? super T> handler) {
        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        delegate.subscribe(interceptedResetHandler(handler, interceptorChain));
    }

    @Nonnull
    private ResetHandler interceptedResetHandler(
            MessageHandlingMember<? super T> handler,
            MessageHandlerInterceptorMemberChain<T> interceptorChain
    ) {
        return (resetContext, ctx) ->
                interceptorChain.handle(
                        resetContext,
                        ctx,
                        target,
                        handler
                ).ignoreEntries().cast();
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull ResetContext resetContext, @Nonnull ProcessingContext context) {
        return delegate.handle(resetContext, context);
    }

    @Nonnull
    @Override
    public ResetHandlerRegistry subscribe(@Nonnull ResetHandler resetHandler) {
        return delegate.subscribe(resetHandler);
    }

    /**
     * @implNote This implementation will consider any class that has a single handler that accepts a replay, to support
     * reset. If no handlers explicitly indicate whether replay is supported, the method returns {@code true}.
     */
    @Override
    public boolean supportsReset() {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(AnnotatedEventHandlingComponent::supportsReplay);
    }

    private static boolean supportsReplay(MessageHandlingMember<?> h) {
        return h.attribute(HandlerAttributes.ALLOW_REPLAY).map(Boolean.TRUE::equals).orElse(Boolean.TRUE);
    }
    // endregion
}
