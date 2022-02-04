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

import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

class StreamableFluxResult implements StreamableResult {

    private final Subscription subscription;

    public <T> StreamableFluxResult(QueryResponseMessage<T> resultMessage,
                                    ReplyChannel<QueryResponse> responseHandler,
                                    QuerySerializer serializer,
                                    String requestId,
                                    String clientId) {
        Flux<T> result = resultMessage.getPayload() == null ? Flux.empty() : (Flux<T>) resultMessage.getPayload();
        SendingSubscriber subscriber = new SendingSubscriber(responseHandler, clientId, requestId);
        this.subscription = subscriber;
        result.map(payload -> convert(resultMessage, payload))
              .map(message -> serialize(serializer, requestId, message))
              .subscribeWith(subscriber);
    }

    @Override
    public void request(long requested) {
        subscription.request(requested);
    }

    @Override
    public void cancel() {
        subscription.cancel();
    }

    private <T> GenericQueryResponseMessage<T> convert(QueryResponseMessage<T> resultMessage, T payload) {
        GenericMessage<T> delegate = new GenericMessage<>(resultMessage.getIdentifier(),
                                                          resultMessage.getPayloadType(),
                                                          payload,
                                                          resultMessage.getMetaData());
        return new GenericQueryResponseMessage<>(delegate);
    }

    private <T> QueryResponse serialize(QuerySerializer serializer, String requestId,
                                        GenericQueryResponseMessage<T> message) {
        return serializer.serializeResponse(message, requestId)
                         .toBuilder()
                         .setStreamed(true)
                         .build();
    }

    private static class SendingSubscriber extends BaseSubscriber<QueryResponse> {

        private final ReplyChannel<QueryResponse> responseHandler;
        private final String clientId;
        private final String requestId;

        public SendingSubscriber(ReplyChannel<QueryResponse> responseHandler, String clientId, String requestId) {
            this.responseHandler = responseHandler;
            this.clientId = clientId;
            this.requestId = requestId;
        }

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            // we want to avoid auto request
        }

        @Override
        protected void hookOnNext(QueryResponse value) {
            responseHandler.send(value);
        }

        @Override
        protected void hookOnComplete() {
            responseHandler.complete();
        }

        @Override
        protected void hookOnError(Throwable e) {
            ErrorMessage ex = ExceptionSerializer.serialize(clientId, e);
            QueryResponse response =
                    QueryResponse.newBuilder()
                                 .setErrorCode(ErrorCode.getQueryExecutionErrorCode(e).errorCode())
                                 .setErrorMessage(ex)
                                 .setRequestIdentifier(requestId)
                                 .build();
            responseHandler.sendLast(response);
        }

        @Override
        protected void hookOnCancel() {
            responseHandler.complete();
        }
    }
}
