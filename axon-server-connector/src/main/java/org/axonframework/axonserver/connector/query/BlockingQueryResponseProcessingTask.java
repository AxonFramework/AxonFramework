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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.common.AxonException;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

class BlockingQueryResponseProcessingTask<R> implements PrioritizedRunnable {

    private final Publisher<QueryResponse> result;
    private final QuerySerializer serializer;
    private final CompletableFuture<QueryResponseMessage<R>> queryTransaction;
    private final long priority;
    private final ResponseType<R> expectedResponseType;

    public BlockingQueryResponseProcessingTask(Publisher<QueryResponse> result,
                                               QuerySerializer serializer,
                                               CompletableFuture<QueryResponseMessage<R>> queryTransaction,
                                               long priority,
                                               ResponseType<R> expectedResponseType) {
        this.result = result;
        this.serializer = serializer;
        this.queryTransaction = queryTransaction;
        this.priority = priority;
        this.expectedResponseType = expectedResponseType;
    }

    public long priority() {
        return priority;
    }

    @Override
    public void run() {
        ConcurrentLinkedQueue<QueryResponse> elements = new ConcurrentLinkedQueue<>();
        result.subscribe(new Subscriber<QueryResponse>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(QueryResponse queryResponse) {
                elements.add(queryResponse);
            }

            @Override
            public void onError(Throwable t) {
                AxonException exception = ErrorCode.QUERY_DISPATCH_ERROR.convert(t);
                queryTransaction.completeExceptionally(exception);
            }

            @Override
            public void onComplete() {
                complete(elements);
            }
        });
    }

    private void complete(ConcurrentLinkedQueue<QueryResponse> elements) {
        QueryResponse peeked = elements.peek();
        if (peeked != null && (!peeked.getStreamed() || expectedResponseType instanceof InstanceResponseType)) {
            queryTransaction.complete(serializer.deserializeResponse(elements.poll(), expectedResponseType));
        } else {
            ResponseType<?> responseType = ResponseTypes.instanceOf(expectedResponseType.getExpectedResponseType());
            //noinspection unchecked
            R payload = (R) elements.stream()
                                    .map(qr -> serializer.deserializeResponse(qr, responseType))
                                    .map(Message::getPayload)
                                    .collect(Collectors.toList());

            QueryResponseMessage<?> peekedResponse = serializer.deserializeResponse(peeked, responseType);
            String identifier = peekedResponse.getIdentifier();
            MetaData metaData = peekedResponse.getMetaData();
            GenericMessage<R> delegate = new GenericMessage<>(identifier, payload, metaData);
            queryTransaction.complete(new GenericQueryResponseMessage<>(delegate));
        }
    }
}
