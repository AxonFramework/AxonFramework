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

import io.axoniq.axonhub.UnsubscriptionQueryRequest;
import io.axoniq.axonhub.UnsubscriptionQueryRequest.Builder;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;

import java.util.function.Function;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionCancel implements Function<String, QueryProviderOutbound> {

    private final AxonHubConfiguration conf;

    public SubscriptionCancel(AxonHubConfiguration configuration) {
        this.conf = configuration;
    }


    @Override
    public QueryProviderOutbound apply(String subscriptionId) {
        Builder builder = UnsubscriptionQueryRequest.newBuilder()
                                                    .setSubscriptionIdentifier(subscriptionId)
                                                    .setClientId(conf.getClientName())
                                                    .setComponentName(conf.getComponentName());
        if (conf.getContext() != null) builder.setContext(conf.getContext());
        return QueryProviderOutbound.newBuilder().setUnsubscriptionQueryRequest(builder.build()).build();
    }
}
