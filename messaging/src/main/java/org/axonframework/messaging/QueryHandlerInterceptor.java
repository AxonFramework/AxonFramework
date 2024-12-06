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

package org.axonframework.messaging;

import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.annotation.Nonnull;

/**
 * Workflow interface that allows for customized query handler invocation chains. A QueryHandlerInterceptor can add
 * customized behavior to query handler invocations, both before and after the invocation.
 *
 * @param <T> The query message type this interceptor can process
 * @author Christian Thiel
 * @since 4.11
 */
public interface QueryHandlerInterceptor<T extends QueryMessage<?, ?>> extends MessageHandlerInterceptor<T> {

    /**
     * Invoked before a QueryMessage is handled by a designated {@link QueryHandler}.
     * <p/>
     * The interceptor is responsible for the continuation of the handling process by invoking the {@link
     * InterceptorChain#proceed()} method on the given {@code interceptorChain}.
     * <p/>
     * The given {@code unitOfWork} contains contextual information. Any information gathered by interceptors
     * may be attached to the unitOfWork.
     * <p/>
     * Interceptors are highly recommended not to change the type of the query message handling result, as the dispatching
     * component might expect a result of a specific type.
     *
     * @param unitOfWork       The UnitOfWork that is processing the query message
     * @param interceptorChain The interceptor chain that allows this interceptor to proceed the dispatch process
     * @return the result of the query handler. May have been modified by interceptors.
     *
     * @throws Exception any exception that occurs while handling the query message
     */
    Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception;
}
