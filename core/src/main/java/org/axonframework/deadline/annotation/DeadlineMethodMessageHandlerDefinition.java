/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.deadline.annotation;

import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

/**
 * Implementation of a {@link HandlerEnhancerDefinition} that is used for {@link DeadlineHandler} annotated methods.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DeadlineMethodMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @SuppressWarnings("unchecked")
    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(DeadlineHandler.class)
                       .map(attr -> (MessageHandlingMember<T>) new DeadlineMethodMessageHandlingMember(original))
                       .orElse(original);
    }

    private class DeadlineMethodMessageHandlingMember<T> extends WrappedMessageHandlingMember<T>
            implements DeadlineHandlingMember<T> {

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        protected DeadlineMethodMessageHandlingMember(MessageHandlingMember<T> delegate) {
            super(delegate);
        }
    }
}
