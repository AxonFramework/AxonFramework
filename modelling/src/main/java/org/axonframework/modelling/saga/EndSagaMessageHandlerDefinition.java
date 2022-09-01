/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link HandlerEnhancerDefinition} inspecting the existence of the {@link EndSaga} annotation on {@link
 * MessageHandlingMember}s. If present, the given {@code MessageHandlingMember} will be wrapped in a {@link
 * EndSageMessageHandlingMember}.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class EndSagaMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull
    <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.hasAnnotation(EndSaga.class) ? new EndSageMessageHandlingMember<>(original) : original;
    }

    /**
     * A {@link WrappedMessageHandlingMember} implementation dedicated towards {@link MessageHandlingMember}s annotated
     * with {@link EndSaga}. After invocation of the {@link #handle(Message, Object)} method, the saga's is ended
     * through the {@link SagaLifecycle#end()} method.
     *
     * @param <T> the entity type wrapped by this {@link MessageHandlingMember}
     */
    public static class EndSageMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        protected EndSageMessageHandlingMember(MessageHandlingMember<T> delegate) {
            super(delegate);
        }

        @Override
        public Object handle(@Nonnull Message<?> message, @Nullable T target) throws Exception {
            try {
                return super.handle(message, target);
            } finally {
                SagaLifecycle.end();
            }
        }
    }
}
