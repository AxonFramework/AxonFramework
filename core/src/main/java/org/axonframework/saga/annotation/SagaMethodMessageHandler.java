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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.PropertyAccessor;
import org.axonframework.common.annotation.PropertyAccessorFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.SagaCreationPolicy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import static java.lang.String.format;

/**
 * A data holder containing information of {@link SagaEventHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SagaMethodMessageHandler implements Comparable<SagaMethodMessageHandler> {

    private static final SagaMethodMessageHandler NO_HANDLER_CONFIGURATION =
            new SagaMethodMessageHandler(SagaCreationPolicy.NONE, null, null, null);

    /**
     * Returns a SagaMethodMessageHandler indicating that a inspected method is *not* a SagaEventHandler.
     *
     * @return a SagaMethodMessageHandler indicating that a inspected method is *not* a SagaEventHandler
     */
    public static SagaMethodMessageHandler noHandler() {
        return NO_HANDLER_CONFIGURATION;
    }

    private final SagaCreationPolicy creationPolicy;
    private final MethodMessageHandler handlerMethod;
    private final String associationKey;
    private final Method associationProperty;

    /**
     * Create a SagaMethodMessageHandler for the given <code>methodHandler</code>. The SagaMethodMessageHandler add
     * information specific to the behavior of Sagas, such as the association value and creation policy.
     *
     * @param methodHandler The handler for incoming events
     * @return a SagaMethodMessageHandler for the handler
     */
    public static SagaMethodMessageHandler getInstance(MethodMessageHandler methodHandler) {
        Method handlerMethod = methodHandler.getMethod();
        SagaEventHandler handlerAnnotation = handlerMethod.getAnnotation(SagaEventHandler.class);
        PropertyAccessor propertyAccessor = PropertyAccessorFactory.createFor(handlerAnnotation.accessor());
        String associationPropertyName = handlerAnnotation.associationProperty();
        Class<?> messageType = methodHandler.getPayloadType();
        Method associationProperty;
        try {
            associationProperty =  propertyAccessor.methodFor(associationPropertyName, messageType);
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException(
                    format("SagaEventHandler %s.%s defines a property %s" +
                            "that is not defined on the Event it declares to handle (%s). " +
                            "Tried to access property using %s",
                            handlerMethod.getDeclaringClass().getName(),
                            methodHandler.getMethodName(),
                            associationPropertyName,
                            methodHandler.getPayloadType().getName(),
                            propertyAccessor.getClass().getSimpleName()),
                    e);
        }
        String associationKey = handlerAnnotation.keyName().isEmpty()
                ? associationPropertyName
                : handlerAnnotation.keyName();
        StartSaga startAnnotation = handlerMethod.getAnnotation(StartSaga.class);
        SagaCreationPolicy sagaCreationPolicy;
        if (startAnnotation == null) {
            sagaCreationPolicy = SagaCreationPolicy.NONE;
        } else if (startAnnotation.forceNew()) {
            sagaCreationPolicy = SagaCreationPolicy.ALWAYS;
        } else {
            sagaCreationPolicy = SagaCreationPolicy.IF_NONE_FOUND;
        }
        return new SagaMethodMessageHandler(sagaCreationPolicy, methodHandler, associationKey, associationProperty);
    }

    /**
     * Creates a SagaMethodMessageHandler.
     *
     * @param creationPolicy      The creation policy for the handlerMethod
     * @param handler             The handler for the event
     * @param associationKey      The association key configured for this handler
     * @param associationProperty The association property configured for this handler
     */
    protected SagaMethodMessageHandler(SagaCreationPolicy creationPolicy, MethodMessageHandler handler,
                                       String associationKey, Method associationProperty) {
        this.creationPolicy = creationPolicy;
        this.handlerMethod = handler;
        this.associationKey = associationKey;
        this.associationProperty = associationProperty;
    }

    /**
     * Indicates whether the inspected method is an Event Handler.
     *
     * @return true if the saga has a handler
     */
    public boolean isHandlerAvailable() {
        return handlerMethod != null;
    }

    /**
     * The AssociationValue to find the saga instance with, or <code>null</code> if no AssociationValue can be found on
     * the given <code>eventMessage</code>.
     *
     * @param eventMessage The event message containing the value of the association
     * @return the AssociationValue to find the saga instance with, or <code>null</code> if none found
     */
    public AssociationValue getAssociationValue(EventMessage eventMessage) {
        if (associationProperty == null) {
            return null;
        }
        try {
            Object associationValue = associationProperty.invoke(eventMessage.getPayload());
            return associationValue == null ? null : new AssociationValue(associationKey, associationValue.toString());
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException(
                    format("Error invoking '%s.%s'. Property methods should not throw exceptions",
                           associationProperty.getDeclaringClass().getName(),
                           associationProperty.getName()), e);
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException(format("Cannot access '%s.%s'. Property methods should be accessible.",
                                                        associationProperty.getDeclaringClass().getName(),
                                                        associationProperty.getName()), e);
        }
    }

    /**
     * Returns the creation policy of the inspected method.
     *
     * @return the creation policy of the inspected method
     */
    public SagaCreationPolicy getCreationPolicy() {
        return creationPolicy;
    }

    private static String methodForProperty(String propertyName) {
        return "get" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propertyName.substring(1);
    }

    /**
     * Indicates whether this Handler is suitable for the given <code>message</code>.
     *
     * @param message The message to inspect
     * @return <code>true</code> if this handler can handle the message, otherwise <code>false</code>.
     */
    public boolean matches(EventMessage message) {
        return handlerMethod != null && handlerMethod.matches(message);
    }

    @Override
    public int compareTo(SagaMethodMessageHandler o) {
        if (this.handlerMethod == null && o.handlerMethod == null) {
            return 0;
        } else if (this.handlerMethod == null) {
            return -1;
        } else if (o.handlerMethod == null) {
            return 1;
        }
        return handlerMethod.compareTo(o.handlerMethod);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SagaMethodMessageHandler that = (SagaMethodMessageHandler) o;

        return !(handlerMethod != null ? !handlerMethod.equals(that.handlerMethod) : that.handlerMethod != null);
    }

    @Override
    public int hashCode() {
        return handlerMethod != null ? handlerMethod.hashCode() : 0;
    }
}
