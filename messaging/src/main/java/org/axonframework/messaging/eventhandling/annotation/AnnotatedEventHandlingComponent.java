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

package org.axonframework.messaging.eventhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.StringUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.core.annotation.HandlerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns classes with {@link org.axonframework.messaging.eventhandling.annotation.EventHandler} annotated
 * methods into a {@link EventHandlingComponent}.
 * <p>
 * Each annotated method is subscribed as an {@link EventHandler} at the {@link EventHandlingComponent} with the event
 * name specified by the parameter of that method.
 *
 * @param <T> The type of the annotated event handler.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AnnotatedEventHandlingComponent<T> implements EventHandlingComponent {

    private final T target;
    private final SimpleEventHandlingComponent handlingComponent;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;
    private final EventConverter converter;

    /**
     * Wraps the given {@code annotatedEventHandler}, allowing it to be subscribed to an {@link EventSink} as an
     * {@link EventHandlingComponent}.
     *
     * @param annotatedEventHandler    The object containing the
     *                                 {@link org.axonframework.messaging.eventhandling.annotation.EventHandler}
     *                                 annotated methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the {@link QualifiedName names} for
     *                                 {@link EventMessage EventMessages}.
     * @param converter                The converter to use for converting the payload of the event to the type expected
     *                                 by the handling method.
     */
    public AnnotatedEventHandlingComponent(@Nonnull T annotatedEventHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull HandlerDefinition handlerDefinition,
                                           @Nonnull MessageTypeResolver messageTypeResolver,
                                           @Nonnull EventConverter converter) {
        this.target = requireNonNull(annotatedEventHandler, "The Annotated Event Handler may not be null.");
        this.handlingComponent = SimpleEventHandlingComponent.create(
                "AnnotatedEventHandlingComponent[%s]".formatted(annotatedEventHandler.getClass().getName())
        );
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) annotatedEventHandler.getClass();
        this.model = AnnotatedHandlerInspector.inspectType(clazz, parameterResolverFactory, handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");
        this.converter = requireNonNull(converter, "The EventConverter may not be null.");

        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        model.getUniqueHandlers(target.getClass(), EventMessage.class)
             .forEach(handler -> registerHandler((EventHandlingMember<? super T>) handler));
    }

    private void registerHandler(EventHandlingMember<? super T> handler) {
        Class<?> payloadType = handler.payloadType();
        QualifiedName qualifiedName = handler.unwrap(MethodEventHandlerDefinition.MethodEventMessageHandlingMember.class)
                                             .map(EventHandlingMember::eventName)
                                             // Filter empty Strings to  fall back to the MessageTypeResolver
                                             .filter(StringUtils::nonEmpty)
                                             .map(QualifiedName::new)
                                             .orElseGet(() -> messageTypeResolver.resolveOrThrow(payloadType)
                                                                                 .qualifiedName());

        handlingComponent.subscribe(qualifiedName, constructEventHandlerFor(qualifiedName, handler));
    }

    private EventHandler constructEventHandlerFor(QualifiedName qualifiedName,
                                                  EventHandlingMember<? super T> handler) {
        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        EventHandler interceptedHandler =
                (event, context) -> interceptorChain.handle(
                                                            event.withConvertedPayload(
                                                                    handler.payloadType(), converter
                                                            ),
                                                            context,
                                                            target,
                                                            handler
                                                    )
                                                    .ignoreEntries()
                                                    .cast();

        return whenCustomSequencingPolicyOn(handler)
                .map(sequencingPolicy -> {
                    String name = "CustomPolicyHandler[%s]".formatted(handler.signature());
                    SimpleEventHandlingComponent handlerWithCustomPolicy =
                            SimpleEventHandlingComponent.create(name, sequencingPolicy);
                    handlerWithCustomPolicy.subscribe(qualifiedName, interceptedHandler);
                    return handlerWithCustomPolicy;
                })
                .map(component -> (EventHandler) component)
                .orElse(interceptedHandler);
    }

    private Optional<SequencingPolicy> whenCustomSequencingPolicyOn(MessageHandlingMember<? super T> handler) {
        return handler.unwrap(MethodSequencingPolicyEventHandlerDefinition.SequencingPolicyEventMessageHandlingMember.class)
                      .map(MethodSequencingPolicyEventHandlerDefinition.SequencingPolicyEventMessageHandlingMember::sequencingPolicy);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        return handlingComponent.handle(event, context);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return Set.copyOf(handlingComponent.supportedEvents());
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return handlingComponent.sequenceIdentifierFor(event, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("target", target);
        descriptor.describeWrapperOf(handlingComponent);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
        descriptor.describeProperty("converter", converter);
    }
}
