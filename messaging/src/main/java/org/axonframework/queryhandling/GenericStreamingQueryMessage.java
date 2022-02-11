/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.FluxResponseType;
import reactor.core.publisher.Flux;

import java.util.Map;

public class GenericStreamingQueryMessage<Q, R> extends GenericQueryMessage<Q, Flux<R>>
        implements StreamingQueryMessage<Q, R> {

    public GenericStreamingQueryMessage(Q payload, Class<R> responseType) {
        this(payload, new FluxResponseType<>(responseType));
    }

    public GenericStreamingQueryMessage(Q payload, String queryName, Class<R> responseType) {
        this(payload, queryName, new FluxResponseType<>(responseType));
    }

    public GenericStreamingQueryMessage(Q payload, FluxResponseType<R> responseType) {
        super(payload, responseType);
    }

    public GenericStreamingQueryMessage(Q payload, String queryName, FluxResponseType<R> responseType) {
        this(new GenericMessage<>(payload, MetaData.emptyInstance()), queryName, responseType);
    }

    public GenericStreamingQueryMessage(Message<Q> delegate, String queryName, FluxResponseType<R> responseType) {
        super(delegate, queryName, responseType);
    }

    @Override
    public StreamingQueryMessage<Q, R> withMetaData(Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().withMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    public StreamingQueryMessage<Q, R> andMetaData(Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().andMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    public FluxResponseType<R> getResponseType() {
        return (FluxResponseType<R>) super.getResponseType();
    }
}
