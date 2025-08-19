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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Interceptor that allows messages to be intercepted and modified before they are dispatched. This interceptor provides
 * a very early means to alter or reject Messages, even before any Unit of Work is created.
 *
 * @param <M> The message type this interceptor can process
 * @author Allard Buijze
 * @since 2.0
 */
public interface MessageDispatchInterceptor<M extends Message<?>> {


    @Nonnull
    MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                         @Nullable ProcessingContext context,
                                         @Nonnull MessageDispatchInterceptorChain<M> interceptorChain);
}
