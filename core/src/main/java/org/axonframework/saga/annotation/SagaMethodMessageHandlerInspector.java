/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.common.annotation.AbstractAnnotatedHandlerDefinition;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class that inspects annotation on a Saga instance and returns the relevant configuration for its Event
 * Handlers.
 *
 * @param <T> The type of saga targeted by this inspector
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaMethodMessageHandlerInspector<T extends AbstractAnnotatedSaga> {

    private static final Logger logger = LoggerFactory.getLogger(SagaMethodMessageHandlerInspector.class);

    private static final ConcurrentMap<Class<?>, SagaMethodMessageHandlerInspector> INSPECTORS = new ConcurrentHashMap<Class<?>, SagaMethodMessageHandlerInspector>();

    private final Set<SagaMethodMessageHandler> handlers = new TreeSet<SagaMethodMessageHandler>();
    private final Class<T> sagaType;
    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Returns a SagaMethodMessageHandlerInspector for the given <code>sagaType</code>. The inspector provides
     * information about @SagaEventHandler annotated handler methods.
     *
     * @param sagaType                 The type of Saga to get the inspector for
     * @param parameterResolverFactory The factory for parameter resolvers that resolve parameters for the annotated
     *                                 methods
     * @param <T>                      The type of Saga to get the inspector for
     * @return The inspector for the given saga type
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractAnnotatedSaga> SagaMethodMessageHandlerInspector<T> getInstance(
            Class<T> sagaType, ParameterResolverFactory parameterResolverFactory) {
        SagaMethodMessageHandlerInspector<T> sagaInspector = INSPECTORS.get(sagaType);
        if (sagaInspector == null || sagaInspector.getParameterResolverFactory() != parameterResolverFactory) {
            sagaInspector = new SagaMethodMessageHandlerInspector<T>(sagaType, parameterResolverFactory);

            INSPECTORS.put(sagaType, sagaInspector);
        }
        return sagaInspector;
    }

    /**
     * Initialize the inspector to handle events for the given <code>sagaType</code>.
     *
     * @param sagaType                 The type of saga this inspector handles
     * @param parameterResolverFactory The factory for parameter resolvers that resolve parameters for the annotated
     *                                 methods
     */
    protected SagaMethodMessageHandlerInspector(Class<T> sagaType, ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(
                sagaType, parameterResolverFactory, true,
                AnnotatedHandlerDefinition.INSTANCE);
        for (MethodMessageHandler handler : inspector.getHandlers()) {
            handlers.add(SagaMethodMessageHandler.getInstance(handler));
        }
        this.sagaType = sagaType;
    }

    /**
     * Find the configuration for the handlers for the given <code>event</code>. If no suitable handler is found, an
     * empty list is returned.
     * The handlers are returned in the order they should be inspected to match against the association value. The
     * first handler in the list to match an association value of the Saga instance should be used to invoke that saga.
     *
     * @param event The Event to investigate the handler for
     * @return the configuration of the handlers, as defined by the annotations.
     */
    public List<SagaMethodMessageHandler> getMessageHandlers(EventMessage event) {
        List<SagaMethodMessageHandler> found = new ArrayList<SagaMethodMessageHandler>(1);
        for (SagaMethodMessageHandler handler : handlers) {
            if (handler.matches(event)) {
                found.add(handler);
            }
        }
        return found;
    }

    /**
     * Finds the handler method on given <code>target</code> for the given <code>event</code>.
     *
     * @param target The instance to find a handler method on
     * @param event  The event to find a handler for
     * @return the most suitable handler for the event on the target, or an instance describing no such handler exists
     */
    public SagaMethodMessageHandler findHandlerMethod(AbstractAnnotatedSaga target, EventMessage event) {
        for (SagaMethodMessageHandler handler : getMessageHandlers(event)) {
            final AssociationValue associationValue = handler.getAssociationValue(event);
            if (target.getAssociationValues().contains(associationValue)) {
                return handler;
            } else if (logger.isDebugEnabled()) {
                logger.debug(
                        "Skipping handler [{}], it requires an association value [{}:{}] that this Saga is not associated with",
                        handler.getName(),
                        associationValue.getKey(),
                        associationValue.getValue());
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("No suitable handler was found for event of type", event.getPayloadType().getName());
        }
        return SagaMethodMessageHandler.noHandler();
    }

    /**
     * Returns the type of saga this inspector handles.
     *
     * @return the type of saga (Class) this inspector handles
     */
    @SuppressWarnings({"unchecked"})
    public Class<T> getSagaType() {
        return sagaType;
    }

    /**
     * Returns the ParameterResolverFactory used by this inspector to resolve values for handler parameters
     *
     * @return the ParameterResolverFactory used by this inspector
     */
    public ParameterResolverFactory getParameterResolverFactory() {
        return parameterResolverFactory;
    }

    private static final class AnnotatedHandlerDefinition extends
            AbstractAnnotatedHandlerDefinition<SagaEventHandler> {

        private static final AnnotatedHandlerDefinition INSTANCE = new AnnotatedHandlerDefinition();

        private AnnotatedHandlerDefinition() {
            super(SagaEventHandler.class);
        }

        @Override
        protected Class<?> getDefinedPayload(SagaEventHandler annotation) {
            return annotation.payloadType();
        }
    }
}
