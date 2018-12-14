/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SubscriptionQueryUpdateMessage decorator to deserialize QueryResponse and QueryUpdate messages.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class DeserializedResult<I, U>
        implements SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> {

    private final SubscriptionQueryResult<QueryResponse, QueryUpdate> delegate;

    private final SubscriptionMessageSerializer serializer;

    public DeserializedResult(
            SubscriptionQueryResult<QueryResponse, QueryUpdate> delegate,
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
