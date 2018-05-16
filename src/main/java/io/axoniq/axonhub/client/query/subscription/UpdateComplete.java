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

import io.axoniq.axonhub.QueryUpdateComplete;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.platform.grpc.PlatformInboundInstruction;

import java.util.function.Function;

import static io.axoniq.platform.grpc.PlatformInboundInstruction.newBuilder;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class UpdateComplete implements Function<String, PlatformInboundInstruction> {

    private final AxonHubConfiguration conf;

    public UpdateComplete(AxonHubConfiguration configuration) {
        this.conf = configuration;
    }


    @Override
    public PlatformInboundInstruction apply(String subscriptionId) {
        QueryUpdateComplete.Builder builder = QueryUpdateComplete.newBuilder()
                                                                 .setSubscriptionIdentifier(subscriptionId)
                                                                 .setClientName(conf.getClientName())
                                                                 .setComponentName(conf.getComponentName());
        if (conf.getContext() != null) builder.setContext(conf.getContext());
        return newBuilder().setQueryUpdateComplete(builder.build()).build();
    }
}
