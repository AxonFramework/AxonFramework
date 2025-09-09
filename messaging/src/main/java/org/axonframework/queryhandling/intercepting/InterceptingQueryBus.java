/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.queryhandling.intercepting;

// TODO 3488 - Introduce handler and dispatch interceptor logic here.
public class InterceptingQueryBus {

    //    private final List<MessageHandlerInterceptor<QueryMessage>> handlerInterceptors = new CopyOnWriteArrayList<>();
    //    private final List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    //    private <Q, R, T extends QueryMessage> T intercept(T query) {
    //        /*
    //        // TODO #3488 - Reintegrate, and construct chain only once!
    //        return new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
    //                .proceed(query, null)
    //                .first()
    //                .<T>cast()
    //                .asMono()
    //                .map(MessageStream.Entry::message)
    //                .block();
    //         */
    //        return query;
    //    }

    //    /**
    //     * Registers an interceptor that is used to intercept Queries before they are passed to their respective handlers.
    //     * The interceptor is invoked separately for each handler instance (in a separate unit of work).
    //     *
    //     * @param interceptor the interceptor to invoke before passing a Query to the handler
    //     * @return handle to deregister the interceptor
    //     */
    //    public Registration registerHandlerInterceptor(
    //            @Nonnull MessageHandlerInterceptor<QueryMessage> interceptor) {
    //        handlerInterceptors.add(interceptor);
    //        return () -> handlerInterceptors.remove(interceptor);
    //    }
    //
    //    /**
    //     * Registers an interceptor that intercepts Queries as they are sent. Each interceptor is called once, regardless of
    //     * the type of query (point-to-point or scatter-gather) executed.
    //     *
    //     * @param interceptor the interceptor to invoke when sending a Query
    //     * @return handle to deregister the interceptor
    //     */
    //    public @Nonnull
    //    Registration registerDispatchInterceptor(
    //            @Nonnull MessageDispatchInterceptor<? super QueryMessage> interceptor) {
    //        dispatchInterceptors.add(interceptor);
    //        return () -> dispatchInterceptors.remove(interceptor);
    //    }

}
