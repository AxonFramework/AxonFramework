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

import io.axoniq.axonhub.QueryUpdateCompleteExceptionally;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.ErrorCode;
import io.axoniq.axonhub.client.util.ExceptionSerializer;
import io.axoniq.platform.grpc.PlatformInboundInstruction;

import java.util.function.BiFunction;

import static io.axoniq.platform.grpc.PlatformInboundInstruction.newBuilder;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class UpdateCompleteExceptionally implements BiFunction<String,Throwable,PlatformInboundInstruction> {

    private final AxonHubConfiguration conf;

    public UpdateCompleteExceptionally(AxonHubConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public PlatformInboundInstruction apply(String subscriptionId, Throwable cause) {
        QueryUpdateCompleteExceptionally.Builder builder = QueryUpdateCompleteExceptionally
                .newBuilder()
                .setSubscriptionIdentifier(subscriptionId)
                .setMessage(ExceptionSerializer.serialize(conf.getClientName(), cause))
                .setErrorCode(ErrorCode.resolve(cause).errorCode())
                .setClientName(conf.getClientName())
                .setComponentName(conf.getComponentName());
        if (conf.getContext() != null) builder.setContext(conf.getContext());
        return newBuilder().setQueryUpdateCompleteExceptionally(builder.build()).build();
    }
}
