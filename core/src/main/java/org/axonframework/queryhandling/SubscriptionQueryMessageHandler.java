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

package org.axonframework.queryhandling;

/**
 * Defines the contract for handlers capable of handling subscription queries.
 *
 * @param <Q> the type of message to be handled (must extend {@link QueryMessage})
 * @param <I> the type of initial response
 * @param <U> the type of incremental responses
 * @author Milan Savic
 * @since 3.3
 */
@FunctionalInterface
public interface SubscriptionQueryMessageHandler<Q extends QueryMessage<?, I>, I, U> {

    /**
     * Delegates handling of given message to the query handler and injects given emitter to it.
     *
     * @param message the message to be handled
     * @param emitter used to inform the caller about initial query result, incremental updates, when there are no more
     *                updates and when an error occurred
     * @return the initial request for the information
     *
     * @throws Exception propagated from the query handler
     */
    I handle(Q message, QueryUpdateEmitter<U> emitter) throws Exception;
}
