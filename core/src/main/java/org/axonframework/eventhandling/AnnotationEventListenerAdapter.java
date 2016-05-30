/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.annotation.AnnotatedHandlerInspector;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.common.annotation.ParameterResolverFactory;

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
    @SuppressWarnings("unchecked")
    public AnnotationEventListenerAdapter(Object annotatedEventListener,
                                          ParameterResolverFactory parameterResolverFactory) {
        this.annotatedEventListener = annotatedEventListener;
        this.listenerType = annotatedEventListener.getClass();
        this.inspector = AnnotatedHandlerInspector.inspectType((Class<Object>)annotatedEventListener.getClass(),
                                                               parameterResolverFactory);
    }

    @Override
    public void handle(EventMessage event) throws Exception {
        for (MessageHandler<? super Object> handler : inspector.getHandlers()) {
            if (handler.canHandle(event)) {
                handler.handle(event, annotatedEventListener);
                break;
            }
        }
    }

    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }
}
