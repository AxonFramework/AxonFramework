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

package org.axonframework.eventhandling.saga;

import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

/**
 * A data holder containing information of {@link SagaEventHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SagaMethodMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

    private final MessageHandlingMember<T> delegate;
    private final SagaCreationPolicy creationPolicy;
    private final String associationKey;
    private final Property associationProperty;
    private final boolean endingHandler;

    /**
     * Creates a SagaMethodMessageHandler.
     *
     * @param creationPolicy      The creation policy for the handlerMethod
     * @param delegate            The message handler for the event
     * @param associationKey      The association key configured for this handler
     * @param associationProperty The association property configured for this handler
     * @param endingHandler       Flag to indicate if an invocation of the given handler should end the saga
     */
    public SagaMethodMessageHandlingMember(MessageHandlingMember<T> delegate, SagaCreationPolicy creationPolicy,
                                           String associationKey, Property associationProperty, boolean endingHandler) {
        super(delegate);
        this.delegate = delegate;
        this.creationPolicy = creationPolicy;
        this.associationKey = associationKey;
        this.associationProperty = associationProperty;
        this.endingHandler = endingHandler;
    }

    /**
     * The AssociationValue to find the saga instance with, or {@code null} if no AssociationValue can be found on
     * the given {@code eventMessage}.
     *
     * @param eventMessage The event message containing the value of the association
     * @return the AssociationValue to find the saga instance with, or {@code null} if none found
     */
    @SuppressWarnings("unchecked")
    public AssociationValue getAssociationValue(EventMessage<?> eventMessage) {
        if (associationProperty == null) {
            return null;
        }

        Object associationValue = associationProperty.getValue(eventMessage.getPayload());
        return associationValue == null ? null : new AssociationValue(associationKey, associationValue.toString());
    }

    @Override
    public Object handle(Message<?> message, T target) throws Exception {
        return delegate.handle(message, target);
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
     * Indicates whether this handler is one that ends the Saga lifecycle
     *
     * @return {@code true} if the Saga lifecycle ends unconditionally after this call, otherwise
     * {@code false}
     */
    public boolean isEndingHandler() {
        return endingHandler;
    }
}
