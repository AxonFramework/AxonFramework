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

package io.axoniq.axonhub.client.query.subscription;

import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.grpc.stub.StreamObserver;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Sara Pellegrini on 29/05/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionResponseHandler<I, U> implements StreamObserver<SubscriptionQueryResponse> {

    private final BlockingQueue<SubscriptionQueryResponse> updateQueue;

    private final UpdateHandler<I, U> updateHandler;

    private final SubscriptionMessageSerializer serializer;

    private final CompletableFuture<I> completableFuture = new CompletableFuture<>();

    SubscriptionResponseHandler(UpdateHandler<I, U> updateHandler, SubscriptionMessageSerializer serializer) {
        this.updateHandler = updateHandler;
        this.serializer = serializer;
        this.updateQueue = new LinkedBlockingQueue<>();
        this.completableFuture.thenAccept(i -> {
            updateHandler.onInitialResult(i);
            Executors.newSingleThreadExecutor().execute(this::handleUpdates);
        });
    }

    @Override
    public void onNext(SubscriptionQueryResponse response) {
        switch (response.getResponseCase()) {
            case INITIAL_RESPONSE:
                QueryResponseMessage<I> responseMessage = serializer.deserialize(response.getInitialResponse());
                completableFuture.complete(responseMessage.getPayload());
                break;
            default:
                updateQueue.add(response);
        }
    }

    @Override
    public void onError(Throwable t) {
        updateHandler.onCompletedExceptionally(t);
    }

    @Override
    public void onCompleted() {
        updateHandler.onCompleted();
    }

    private void handleUpdates() {
        boolean complete = false;
        while (!complete) {
            try {
                SubscriptionQueryResponse response = updateQueue.take();
                switch (response.getResponseCase()) {
                    case UPDATE:
                        SubscriptionQueryUpdateMessage<U> updateMessage = serializer.deserialize(response.getUpdate());
                        updateHandler.onUpdate(updateMessage.getPayload());
                        break;
                    case COMPLETE:
                        updateHandler.onCompleted();
                        complete = true;
                        break;
                    case COMPLETE_EXCEPTIONALLY:
                        updateHandler.onCompletedExceptionally(serializer.deserialize(response.getCompleteExceptionally()));
                        complete = true;
                        break;
                }
            } catch (InterruptedException e) {
                updateHandler.onCompletedExceptionally(e);
            }
        }
    }
}
