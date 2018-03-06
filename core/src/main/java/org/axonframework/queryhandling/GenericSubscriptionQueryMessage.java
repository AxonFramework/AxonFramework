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

import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.responsetypes.ResponseType;

import java.util.Map;

public class GenericSubscriptionQueryMessage<Q, I, U> extends GenericQueryMessage<Q, I> implements SubscriptionQueryMessage<Q, I, U> {

    private final Class<U> updateResponseType;

    public GenericSubscriptionQueryMessage(Q payload, ResponseType<I> responseType, Class<U> updateResponseType) {
        super(payload, responseType);
        this.updateResponseType = updateResponseType;
    }

    public GenericSubscriptionQueryMessage(Q payload, String queryName, ResponseType<I> responseType, Class<U> updateResponseType) {
        super(payload, queryName, responseType);
        this.updateResponseType = updateResponseType;
    }

    public GenericSubscriptionQueryMessage(Message<Q> delegate, String queryName, ResponseType<I> responseType, Class<U> updateResponseType) {
        super(delegate, queryName, responseType);
        this.updateResponseType = updateResponseType;
    }

    @Override
    public Class<U> getUpdateResponseType() {
        return updateResponseType;
    }

    @Override
    public QueryMessage<Q, I> withMetaData(Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().withMetaData(metaData), getQueryName(), getResponseType(), updateResponseType);
    }

    @Override
    public QueryMessage<Q, I> andMetaData(Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().andMetaData(metaData), getQueryName(), getResponseType(), updateResponseType);
    }
}
