/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import jakarta.annotation.Nonnull;

/**
 * Used to derive the value of an association property by looking it up the event message's {@link
 * org.axonframework.messaging.MetaData}.
 *
 * @author Sofia Guy Ang
 */
public class MetaDataAssociationResolver implements AssociationResolver {

    /**
     * Does nothing because we can only check for existence of property in the metadata during event handling.
     */
    @Override
    public <T> void validate(@Nonnull String associationPropertyName, @Nonnull MessageHandlingMember<T> handler) {
        // Do nothing
    }

    /**
     * Finds the association property value by looking up the association property name in the event message's {@link
     * org.axonframework.messaging.MetaData}.
     */
    @Override
    public <T> Object resolve(@Nonnull String associationPropertyName, @Nonnull EventMessage<?> message,
                              @Nonnull MessageHandlingMember<T> handler) {
        return message.getMetaData().get(associationPropertyName);
    }
}
