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

import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;

import static io.axoniq.platform.grpc.PlatformOutboundInstruction.RequestCase.*;

/**
 * Created by Sara Pellegrini on 03/05/2018.
 * sara.pellegrini@gmail.com
 */
@Component
public class AxonHubUpdateEmitter implements QueryUpdateEmitter {

    private final Logger logger = LoggerFactory.getLogger(AxonHubUpdateEmitter.class);

    private final PlatformConnectionManager platformConnectionManager;
    private final SubscriptionQuerySerializer messageMapping;

    private final Map<String, SubscriptionQueryMessage<?, ?, ?>> subscriptions = new ConcurrentHashMap<>();

    public AxonHubUpdateEmitter(PlatformConnectionManager platformConnectionManager,
                                Serializer messageSerializer, Serializer genericSerializer,
                                AxonHubConfiguration configuration) {
        this.platformConnectionManager = platformConnectionManager;
        this.messageMapping = new SubscriptionQuerySerializer(configuration, messageSerializer, genericSerializer);
    }

    @PostConstruct
    private void init() {
        platformConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        platformConnectionManager.onOutboundInstruction(SUBSCRIBE_QUERY, this::subscribe);
        platformConnectionManager.onOutboundInstruction(UNSUBSCRIBE_QUERY, this::unsubscribe);
    }

    private void onApplicationDisconnected(){
        subscriptions.clear();
    }

    private void subscribe(PlatformOutboundInstruction instruction) {
        SubscriptionQueryMessage subscription = messageMapping.subscriptionQueryMessage(instruction);
        subscriptions.put(subscription.getIdentifier(), subscription);
    }

    private void unsubscribe(PlatformOutboundInstruction instruction) {
        subscriptions.remove(instruction.getUnsubscribeQuery().getSubscriptionIdentifier());
    }


    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         SubscriptionQueryUpdateMessage<U> update) {

        subscriptions.values().stream()
                     .map(sqm -> (SubscriptionQueryMessage<?, ?, U>) sqm)
                     .filter(filter)
                     .forEach(sqm -> {
                         logger.debug("emitting update for subscriptionID " + sqm.getIdentifier());
                         platformConnectionManager.send(messageMapping.serializeUpdate(update, sqm.getIdentifier()));
                     });
    }

    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        send(filter, sqm -> messageMapping.serializeComplete(sqm.getIdentifier()));
    }

    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        send(filter, sqm -> messageMapping.serializeCompleteExceptionally(sqm.getIdentifier(), cause));

    }

    private void send(Predicate<? super SubscriptionQueryMessage<?, ?, ?>> predicate,
                      Function<SubscriptionQueryMessage, PlatformInboundInstruction> instruction) {
        subscriptions.values().stream()
                     .filter(predicate)
                     .forEach(sqm -> {
                         logger.debug("Update completed for subscriptionId " + sqm.getIdentifier());
                         subscriptions.remove(sqm.getIdentifier(), sqm);
                         platformConnectionManager.send(instruction.apply(sqm));
                     });
    }
}
