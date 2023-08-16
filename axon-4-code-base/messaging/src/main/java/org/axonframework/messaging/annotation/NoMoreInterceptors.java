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

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;

import javax.annotation.Nonnull;

/**
 * This will implement {@link MessageHandlerInterceptorMemberChain} with no more interceptors. It can be used a default
 * interceptor, for example in the {@link AnnotatedHandlerInspector}.
 *
 * @param <T> the type of the handlers
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class NoMoreInterceptors<T> implements MessageHandlerInterceptorMemberChain<T> {

    /**
     * Creates and returns a new instance
     *
     * @return a new {@link NoMoreInterceptors} instance
     * @param <T> the type of the handlers
     */
    public static <T> MessageHandlerInterceptorMemberChain<T> instance() {
        return new NoMoreInterceptors<>();
    }

    @Override
    public Object handle(@Nonnull Message<?> message, @Nonnull T target,
                         @Nonnull MessageHandlingMember<? super T> handler) throws Exception {
        return handler.handle(message, target);
    }
}
