/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.AbstractHandlerInspector;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.AssociationValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Utility class that inspects annotation on a Saga instance and returns the relevant configuration for its Event
 * Handlers.
 *
 * @param <T> The type of saga targeted by this inspector
 * @author Allard Buijze
 * @since 0.7
 */
class SagaAnnotationInspector<T extends AbstractAnnotatedSaga> extends AbstractHandlerInspector {

    /**
     * Initialize the inspector.
     *
     * @param sagaType The type of saga this inspector handles
     */
    public SagaAnnotationInspector(Class<T> sagaType) {
        super(sagaType, SagaEventHandler.class);
    }

    /**
     * Find the configuration for the handler on the given <code>sagaType</code> for the given <code>event</code>.
     *
     * @param event The Event to investigate the handler for
     * @return the configuration of the handler, as defined by the annotations.
     */
    public HandlerConfiguration findHandlerConfiguration(EventMessage event) {
        MethodMessageHandler handler = findHandlerMethod(event);
        if (handler == null) {
            return HandlerConfiguration.noHandler();
        }
        Method handlerMethod = handler.getMethod();
        SagaEventHandler handlerAnnotation = handlerMethod.getAnnotation(SagaEventHandler.class);
        String associationProperty = handlerAnnotation.associationProperty();
        Object associationValue = getPropertyValue(event, associationProperty);
        if (associationValue == null) {
            return HandlerConfiguration.noHandler();
        }
        String associationKey = handlerAnnotation.keyName().isEmpty()
                ? associationProperty
                : handlerAnnotation.keyName();
        AssociationValue association = new AssociationValue(associationKey, associationValue.toString());
        StartSaga startAnnotation = handlerMethod.getAnnotation(StartSaga.class);
        EndSaga endAnnotation = handlerMethod.getAnnotation(EndSaga.class);
        return new HandlerConfiguration(creationPolicy(startAnnotation),
                                        handlerMethod,
                                        endAnnotation != null,
                                        association);
    }

    private SagaCreationPolicy creationPolicy(StartSaga startSaga) {
        if (startSaga == null) {
            return SagaCreationPolicy.NONE;
        } else if (startSaga.forceNew()) {
            return SagaCreationPolicy.ALWAYS;
        } else {
            return SagaCreationPolicy.IF_NONE_FOUND;
        }
    }

    private Object getPropertyValue(EventMessage event, String property) {
        try {
            Method m = event.getPayloadType().getMethod("get" + capitalize(property));
            return m.invoke(event.getPayload());
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException(String.format("Cannot find getter for property %s", property), e);
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException(String.format("Error invoking getter for property '%s'", property), e);
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException(String.format("Cannot access getter for property '%s'", property), e);
        }
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1);
    }

    /**
     * Returns the type of saga this inspector handles.
     *
     * @return the type of saga (Class) this inspector handles
     */
    @SuppressWarnings({"unchecked"})
    public Class<T> getSagaType() {
        return (Class<T>) super.getTargetType();
    }
}
