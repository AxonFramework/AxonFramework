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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import reactor.core.CompletableFuture.Flux;
import reactor.core.CompletableFuture.Mono;

/**
 * A decorator of the {@link SubscriptionQueryUpdateMessage} to deserialize a {@link QueryResponseMessage} and {@link
 * QueryUpdate} messages.
 *
 * @param <I> a generic specifying the type of the initial result of the {@link SubscriptionQueryResult}
 * @param <U> a generic specifying the type of the subsequent updates of the {@link SubscriptionQueryResult}
 * @author Sara Pellegrini
 * @since 4.0
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class DeserializedResult<I, U> implements SubscriptionQueryResult<QueryResponseMessage<I>,
        SubscriptionQueryUpdateMessage<U>> {

    private final SubscriptionQueryResult<QueryResponse, QueryUpdate> delegate;
    private final SubscriptionMessageSerializer serializer;

    /**
     * Instantiate a {@link DeserializedResult} wrapping the {@code delegate} which will be serialized by the given
     * {@code serializer}.
     *
     * @param delegate   a {@link SubscriptionQueryResult} which will be wrapped
     * @param serializer a {@link SubscriptionMessageSerializer} used to serialize the initial results and the
     *                   subsequent updates
     */
    public DeserializedResult(SubscriptionQueryResult<QueryResponse, QueryUpdate> delegate,
                              SubscriptionMessageSerializer serializer) {
        this.delegate = delegate;
        this.serializer = serializer;
    }

    @Override
    public Mono<QueryResponseMessage<I>> initialResult() {
        return delegate.initialResult().map(serializer::deserialize);
    }

    @Override
    public Flux<SubscriptionQueryUpdateMessage<U>> updates() {
        return delegate.updates().map(serializer::deserialize);
    }

    @Override
    public boolean cancel() {
        return delegate.cancel();
    }
}
