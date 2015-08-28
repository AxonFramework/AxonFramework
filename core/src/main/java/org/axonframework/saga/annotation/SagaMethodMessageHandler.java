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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.MessageHandlerInvocationException;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.SagaCreationPolicy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    private final Property associationProperty;

    /**
     * Create a SagaMethodMessageHandler for the given <code>methodHandler</code>. The SagaMethodMessageHandler add
     * information specific to the behavior of Sagas, such as the association value and creation policy.
     *
     * @param methodHandler The handler for incoming events
     * @return a SagaMethodMessageHandler for the handler
     */
    @SuppressWarnings("unchecked")
    public static SagaMethodMessageHandler getInstance(MethodMessageHandler methodHandler) {
        Method handlerMethod = methodHandler.getMethod();
        SagaEventHandler handlerAnnotation = handlerMethod.getAnnotation(SagaEventHandler.class);
        String associationPropertyName = handlerAnnotation.associationProperty();
        Property associationProperty = PropertyAccessStrategy.getProperty(methodHandler.getPayloadType(),
                                                                          associationPropertyName);
        if (associationProperty == null) {
            throw new AxonConfigurationException(format("SagaEventHandler %s.%s defines a property %s that is not "
                                                                + "defined on the Event it declares to handle (%s)",
                                                        methodHandler.getMethod().getDeclaringClass().getName(),
                                                        methodHandler.getMethodName(), associationPropertyName,
                                                        methodHandler.getPayloadType().getName()
            ));
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
                                       String associationKey, Property associationProperty) {
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
    @SuppressWarnings("unchecked")
    public AssociationValue getAssociationValue(EventMessage eventMessage) {
        if (associationProperty == null) {
            return null;
        }

        Object associationValue = associationProperty.getValue(eventMessage.getPayload());
        return associationValue == null ? null : new AssociationValue(associationKey, associationValue.toString());
    }

    /**
     * Returns the creation policy of the inspected method.
     *
     * @return the creation policy of the inspected method
     */
    public SagaCreationPolicy getCreationPolicy() {
        return creationPolicy;
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

    /**
     * Indicates whether this handler is one that ends the Saga lifecycle
     *
     * @return <code>true</code> if the Saga lifecycle ends unconditionally after this call, otherwise
     * <code>false</code>
     */
    public boolean isEndingHandler() {
        return handlerMethod != null && handlerMethod.getMethod().isAnnotationPresent(EndSaga.class);
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
        final int handlerEquality = handlerMethod.compareTo(o.handlerMethod);
        if (handlerEquality == 0) {
            return o.handlerMethod.getMethod().getParameterTypes().length
                    - this.handlerMethod.getMethod().getParameterTypes().length;
        }
        return handlerEquality;
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

        return this.compareTo(that) != 0;
    }

    @Override
    public int hashCode() {
        return handlerMethod != null ? handlerMethod.hashCode() : 0;
    }

    /**
     * Invoke a handler on given <code>target</code> for given <code>message</code>.
     *
     * @param target  The instance to invoke a method on
     * @param message The message to use to resolve the parameters of the handler to invoke
     */
    public void invoke(Object target, EventMessage message) {
        if (!isHandlerAvailable()) {
            return;
        }
        try {
            handlerMethod.invoke(target, message);
        } catch (IllegalAccessException e) {
            throw new MessageHandlerInvocationException("Access to the message handler method was denied.", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new MessageHandlerInvocationException("An exception occurred while invoking the handler method.", e);
        }
    }

    /**
     * Returns the name of the handler.
     *
     * @return the name of the handler
     */
    public String getName() {
        return handlerMethod.getMethodName();
    }
}
