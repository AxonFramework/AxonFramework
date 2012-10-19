/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.domain.EventMessage;

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

    private static final ConcurrentMap<Class<?>, SagaMethodMessageHandlerInspector> INSPECTORS = new ConcurrentHashMap<Class<?>, SagaMethodMessageHandlerInspector>();

    private final Set<SagaMethodMessageHandler> handlers = new TreeSet<SagaMethodMessageHandler>();
    private final Class<T> sagaType;

    /**
     * Returns a SagaMethodMessageHandlerInspector for the given <code>sagaType</code>. The inspector provides
     * information about @SagaEventHandler annotated handler methods.
     *
     * @param sagaType The type of Saga to get the inspector for
     * @param <T>      The type of Saga to get the inspector for
     * @return The inspector for the given saga type
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractAnnotatedSaga> SagaMethodMessageHandlerInspector<T> getInstance(
            Class<T> sagaType) {
        SagaMethodMessageHandlerInspector<T> sagaInspector = INSPECTORS.get(sagaType);
        if (sagaInspector == null) {
            sagaInspector = new SagaMethodMessageHandlerInspector<T>(sagaType);
            INSPECTORS.putIfAbsent(sagaType, sagaInspector);
        }
        return sagaInspector;
    }

    /**
     * Initialize the inspector to handle events for the given <code>sagaType</code>.
     *
     * @param sagaType The type of saga this inspector handles
     */
    protected SagaMethodMessageHandlerInspector(Class<T> sagaType) {
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(sagaType,
                                                                                            SagaEventHandler.class,
                                                                                            true);
        for (MethodMessageHandler handler : inspector.getHandlers()) {
            handlers.add(SagaMethodMessageHandler.getInstance(handler));
        }
        this.sagaType = sagaType;
    }

    /**
     * Find the configuration for the handler on the given <code>sagaType</code> for the given <code>event</code>. If
     * no
     * suitable handler is found, the NoOpHandler is returned, that does nothing when invoked.
     *
     * @param event The Event to investigate the handler for
     * @return the configuration of the handler, as defined by the annotations.
     */
    public SagaMethodMessageHandler getMessageHandler(EventMessage event) {
        for (SagaMethodMessageHandler handler : handlers) {
            if (handler.matches(event)) {
                return handler;
            }
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
}
