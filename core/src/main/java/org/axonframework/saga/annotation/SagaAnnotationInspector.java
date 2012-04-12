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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.Event;
import org.axonframework.saga.AssociationValue;
import org.axonframework.util.AbstractHandlerInspector;
import org.axonframework.util.AxonConfigurationException;
import org.axonframework.util.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(SagaAnnotationInspector.class);
    private static volatile boolean deprecatedWarningGiven = false;

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
    public HandlerConfiguration findHandlerConfiguration(Event event) {
        Handler handler = findHandlerMethod(event.getClass());
        if (handler == null) {
            return HandlerConfiguration.noHandler();
        }
        Method handlerMethod = handler.getMethod();
        SagaEventHandler handlerAnnotation = handlerMethod.getAnnotation(SagaEventHandler.class);
        String associationProperty = handlerAnnotation.associationProperty();
        String associationKey = handlerAnnotation.keyName().isEmpty()
                ? associationProperty
                : handlerAnnotation.keyName();
        Object associationValue = getPropertyValue(event, associationProperty);
        if (associationValue == null) {
            return HandlerConfiguration.noHandler();
        }

        if (!deprecatedWarningGiven
                && !String.class.isInstance(associationValue)
                && !AggregateIdentifier.class.isInstance(associationValue)
                && !Number.class.isInstance(associationValue)
                && !associationValue.getClass().isPrimitive()) {
            logger.warn("****************************************************");
            logger.warn("WARNING! Use of deprecated feature: In future versions of Axon only numbers (Integer, Long), "
                                + "Strings, primitive types and AggregateIdentifier instances "
                                + "are accepted as association value.");
            logger.warn("****************************************************");
            deprecatedWarningGiven = true;
        }
        AssociationValue association = new AssociationValue(associationKey, associationValue);
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

    private Object getPropertyValue(Event event, String property) {
        try {
            Method m = event.getClass().getMethod("get" + capitalize(property));
            return m.invoke(event);
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException("", e);
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException("", e);
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException("", e);
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
