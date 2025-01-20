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

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.replay.GenericResetContext;
import org.axonframework.eventhandling.replay.ResetContext;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collection;
import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link EventMessageHandler}.
 *
 * @author Allard Buijze
 * @see EventMessageHandler
 * @since 0.1
 */
public class AnnotationEventHandlerAdapter implements EventMessageHandler {

    private final AnnotatedHandlerInspector<Object> inspector;
    private final Class<?> listenerType;
    private final Object annotatedEventListener;
    private final MessageNameResolver messageNameResolver;

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener) {
        this(annotatedEventListener,
             ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()),
             new ClassBasedMessageNameResolver());
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     * @param messageNameResolver    The {@link MessageNameResolver} resolving the
     *                               {@link org.axonframework.messaging.QualifiedName names} for
     *                               {@link org.axonframework.eventhandling.EventMessage EventMessages}
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener, MessageNameResolver messageNameResolver) {
        this(annotatedEventListener,
             ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()),
             messageNameResolver);
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given
     * {@code parameterResolverFactory} is used to resolve parameter values for handler methods.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     * @param messageNameResolver      The {@link MessageNameResolver} resolving the
     *                                 {@link org.axonframework.messaging.QualifiedName names} for
     *                                 {@link org.axonframework.eventhandling.EventMessage EventMessages}
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener,
                                         ParameterResolverFactory parameterResolverFactory,
                                         MessageNameResolver messageNameResolver) {
        this(annotatedEventListener,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedEventListener.getClass()),
             messageNameResolver
        );
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given
     * {@code parameterResolverFactory} is used to resolve parameter values for handler methods. Handler definition is
     * used to create concrete handlers.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     * @param handlerDefinition        the handler definition used to create concrete handlers
     * @param messageNameResolver      The {@link MessageNameResolver} resolving the
     *                                 {@link org.axonframework.messaging.QualifiedName names} for
     *                                 {@link org.axonframework.eventhandling.EventMessage EventMessages}
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener,
                                         ParameterResolverFactory parameterResolverFactory,
                                         HandlerDefinition handlerDefinition,
                                         MessageNameResolver messageNameResolver) {
        assertNonNull(messageNameResolver, "The Message Name Resolver may not be null");
        this.annotatedEventListener = annotatedEventListener;
        this.listenerType = annotatedEventListener.getClass();
        this.inspector = AnnotatedHandlerInspector.inspectType(annotatedEventListener.getClass(),
                                                               parameterResolverFactory,
                                                               handlerDefinition);
        this.messageNameResolver = messageNameResolver;
    }

    private static boolean supportsReplay(MessageHandlingMember<? super Object> h) {
        return h.attribute(HandlerAttributes.ALLOW_REPLAY).map(Boolean.TRUE::equals).orElse(Boolean.TRUE);
    }

    @Override
    public Object handleSync(EventMessage<?> event) throws Exception {
        Optional<MessageHandlingMember<? super Object>> handler =
                inspector.getHandlers(listenerType)
                         .filter(h -> h.canHandle(event, null))
                         .findFirst();
        if (handler.isPresent()) {
            MessageHandlerInterceptorMemberChain<Object> interceptor = inspector.chainedInterceptor(listenerType);
            return interceptor.handleSync(event, annotatedEventListener, handler.get());
        }
        return null;
    }

    @Override
    public boolean canHandle(EventMessage<?> event) {
        return inspector.getHandlers(listenerType)
                        .anyMatch(h -> h.canHandle(event, null));
    }

    @Override
    public boolean canHandleType(Class<?> payloadType) {
        return inspector.getHandlers(listenerType)
                        .filter(messageHandlingMember -> messageHandlingMember.canHandleMessageType(EventMessage.class))
                        .anyMatch(handler -> handler.canHandleType(payloadType));
    }

    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }

    @Override
    public void prepareReset(ProcessingContext processingContext) {
        prepareReset(null, null);
    }

    @Override
    public <R> void prepareReset(R resetContext, ProcessingContext processingContext) {
        try {
            ResetContext<?> resetMessage = asResetContext(resetContext);
            inspector.getHandlers(listenerType)
                     .filter(h -> h.canHandle(resetMessage, processingContext))
                     .findFirst()
                     .ifPresent(messageHandlingMember -> messageHandlingMember.handle(resetMessage,
                                                                                      processingContext,
                                                                                      annotatedEventListener)
                                                                              .firstAsCompletableFuture()
                                                                              .join());
        } catch (Exception e) {
            throw new ResetNotSupportedException("An Error occurred while notifying handlers of the reset", e);
        }
    }

    /**
     * Returns the given {@code messageOrPayload} as a {@link ResetContext}. If {@code messageOrPayload} already
     * implements {@code ResetContext}, it is returned as-is. If it implements {@link Message}, {@code messageOrPayload}
     * will be cast to {@code Message} and current time is used to create a {@code ResetContext}. Otherwise, the given
     * {@code messageOrPayload} is wrapped into a {@link GenericResetContext} as its payload.
     *
     * @param messageOrPayload the payload to wrap or cast as {@link ResetContext}
     * @param <T>              the type of payload contained in the message
     * @return a {@link ResetContext} containing given {@code messageOrPayload} as payload, or the
     * {@code messageOrPayload} if it already implements {@code ResetContext}.
     */
    @SuppressWarnings("unchecked")
    private <T> ResetContext<T> asResetContext(Object messageOrPayload) {
        if (messageOrPayload instanceof ResetContext) {
            return (ResetContext<T>) messageOrPayload;
        } else if (messageOrPayload instanceof Message) {
            return new GenericResetContext<>((Message<T>) messageOrPayload);
        }
        QualifiedName name = messageOrPayload == null
                ? new QualifiedName("axon.framework", "empty.reset.context", "5.0.0")
                : messageNameResolver.resolve(messageOrPayload);
        return new GenericResetContext<>(name, (T) messageOrPayload);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation will consider any class that has a single handler that accepts a replay, to support
     * reset. If no handlers explicitly indicate whether replay is supported, the method returns {@code true}.
     */
    @Override
    public boolean supportsReset() {
        return inspector.getAllHandlers()
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .anyMatch(AnnotationEventHandlerAdapter::supportsReplay);
    }
}
