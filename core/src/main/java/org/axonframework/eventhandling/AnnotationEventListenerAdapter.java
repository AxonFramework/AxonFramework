/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link
 * org.axonframework.eventhandling.EventListener}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.EventListener
 * @since 0.1
 */
public class AnnotationEventListenerAdapter implements EventListenerProxy {

    private final AnnotatedHandlerInspector<Object> inspector;
    private final Class<?> listenerType;
    private final Object annotatedEventListener;

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener) {
        this(annotatedEventListener, ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given
     * {@code parameterResolverFactory} is used to resolve parameter values for handler methods.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener,
                                          ParameterResolverFactory parameterResolverFactory) {
        this(annotatedEventListener,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given {@code
     * parameterResolverFactory} is used to resolve parameter values for handler methods. Handler definition is used to
     * create concrete handlers.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     * @param handlerDefinition        the handler definition used to create concrete handlers
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener,
                                          ParameterResolverFactory parameterResolverFactory,
                                          HandlerDefinition handlerDefinition) {
        this.annotatedEventListener = annotatedEventListener;
        this.listenerType = annotatedEventListener.getClass();
        this.inspector = AnnotatedHandlerInspector.inspectType(annotatedEventListener.getClass(),
                                                               parameterResolverFactory,
                                                               handlerDefinition);
    }

    @Override
    public void handle(EventMessage<?> event) throws Exception {
        for (MessageHandlingMember<? super Object> handler : inspector.getHandlers()) {
            if (handler.canHandle(event)) {
                handler.handle(event, annotatedEventListener);
                break;
            }
        }
    }

    @Override
    public boolean canHandle(EventMessage<?> event) {
        for (MessageHandlingMember<? super Object> handler : inspector.getHandlers()) {
            if (handler.canHandle(event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }

    @Override
    public void prepareReset() {
        try {
            handle(GenericEventMessage.asEventMessage(new ResetTriggeredEvent()));
        } catch (Exception e) {
            throw new ResetNotSupportedException("An Error occurred while notifying handlers of the reset", e);
        }
    }

    @Override
    public boolean supportsReset() {
        return true;
    }
}
