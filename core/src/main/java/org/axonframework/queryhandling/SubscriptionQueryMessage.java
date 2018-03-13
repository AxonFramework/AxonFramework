/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.queryhandling.responsetypes.ResponseType;

/**
 * Message type that carries a Subscription Query: a request for information. Besides a payload, Subscription Query
 * Messages also carry the expected response type and update type. The response type is the type of result expected by
 * the caller. The update type is type of incremental updates.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type and update type.
 *
 * @param <Q> the type of payload
 * @param <I> the type of initial response
 * @param <U> the type of incremental responses
 * @since 3.3
 */
public interface SubscriptionQueryMessage<Q, I, U> extends QueryMessage<Q, I> {

    /**
     * Returns the type of incremental responses.
     *
     * @return the type of incremental responses
     */
    ResponseType<U> getUpdateResponseType();
}
