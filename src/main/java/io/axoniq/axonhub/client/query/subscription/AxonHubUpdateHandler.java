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

import io.axoniq.axonhub.client.Publisher;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;

/**
 * Created by Sara Pellegrini on 01/06/2018.
 * sara.pellegrini@gmail.com
 */
public class AxonHubUpdateHandler<I,U> implements UpdateHandler<I,U> {

    private final String subscriptionId;

    private final Publisher<QueryProviderOutbound> publisher;

    private final SubscriptionMessageSerializer messageMapping;

    AxonHubUpdateHandler(String subscriptionId,
                                Publisher<QueryProviderOutbound> publisher,
                                SubscriptionMessageSerializer messageMapping) {
        this.subscriptionId = subscriptionId;
        this.publisher = publisher;
        this.messageMapping = messageMapping;
    }


    @Override
    public void onInitialResult(I initial) {
        QueryResponseMessage<I> message = new GenericQueryResponseMessage<>(initial);
        publisher.publish(messageMapping.serialize(message, subscriptionId));
    }

    @Override
    public void onUpdate(U update) {
        SubscriptionQueryUpdateMessage<U> message = new GenericSubscriptionQueryUpdateMessage<>(update);
        publisher.publish(messageMapping.serialize(message, subscriptionId));
    }

    @Override
    public void onCompleted() {
        publisher.publish(messageMapping.serializeComplete(subscriptionId));
    }

    @Override
    public void onCompletedExceptionally(Throwable error) {
        publisher.publish(messageMapping.serializeCompleteExceptionally(subscriptionId,error));
    }
}
