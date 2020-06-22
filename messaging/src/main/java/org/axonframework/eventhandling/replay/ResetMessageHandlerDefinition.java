/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.replay;

import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

/**
 * Implementation of the {@link HandlerEnhancerDefinition} used for {@link ResetHandler} annotated methods.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class ResetMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(ResetHandler.class)
                       .map(attr -> (MessageHandlingMember<T>) new ResetMessageHandlingMember<>(original))
                       .orElse(original);
    }

    private static class ResetMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T> implements ResetHandlingMember<T> {

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        protected ResetMessageHandlingMember(MessageHandlingMember<T> delegate) {
            super(delegate);
        }

        @Override
        public int priority() {
            return 10000 + Math.min(Integer.MAX_VALUE - 10000, super.priority());
        }
    }
}
