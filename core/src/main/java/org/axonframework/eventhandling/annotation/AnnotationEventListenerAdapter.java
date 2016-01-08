/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.EventListenerProxy;
import org.axonframework.eventhandling.EventMessage;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link
 * org.axonframework.eventhandling.EventListener}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.EventListener
 * @since 0.1
 */
public class AnnotationEventListenerAdapter implements EventListenerProxy {

    private final MessageHandlerInvoker invoker;
    private final Class<?> listenerType;

    /**
     * Wraps the given <code>annotatedEventListener</code>, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener) {
        this(annotatedEventListener, ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given <code>annotatedEventListener</code>, allowing it to be subscribed to an Event Bus. The given
     * <code>parameterResolverFactory</code> is used to resolve parameter values for handler methods.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     */
    public AnnotationEventListenerAdapter(Object annotatedEventListener,
                                          ParameterResolverFactory parameterResolverFactory) {
        this.invoker = new MessageHandlerInvoker(annotatedEventListener, parameterResolverFactory, false,
                                                 AnnotatedEventHandlerDefinition.INSTANCE);
        this.listenerType = annotatedEventListener.getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(EventMessage event) {
        invoker.invokeHandlerMethod(event);
    }

    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }
}
