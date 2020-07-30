/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.util;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Interceptor around a GRPC request to add a Token element to the metadata.
 *
 * @author Marc Gathier
 * @since 4.0
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class TokenAddingInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> ACCESS_TOKEN_KEY =
            Metadata.Key.of("AxonIQ-Access-Token", Metadata.ASCII_STRING_MARSHALLER);

    private final String token;

    /**
     * Constructs a {@link TokenAddingInterceptor} to attach the given {@code token}.
     *
     * @param token the token to attach to outgoing messages
     */
    public TokenAddingInterceptor(String token) {
        this.token = token;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                               CallOptions callOptions,
                                                               Channel channel) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                channel.newCall(methodDescriptor, callOptions)
        ) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (token != null) {
                    headers.put(ACCESS_TOKEN_KEY, token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
