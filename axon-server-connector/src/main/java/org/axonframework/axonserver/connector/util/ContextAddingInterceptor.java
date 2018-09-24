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

package org.axonframework.axonserver.connector.util;

import io.grpc.*;

/**
 * Interceptor around a GRPC request to add a Context element to the metadata.
 *
 * @author Marc Gathier
 */
public class ContextAddingInterceptor implements ClientInterceptor {
    static final Metadata.Key<String> CONTEXT_TOKEN_KEY =
            Metadata.Key.of("AxonIQ-Context", Metadata.ASCII_STRING_MARSHALLER);

    private final String token;

    public ContextAddingInterceptor(String token) {
        this.token = token;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(methodDescriptor, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if( token != null) headers.put(CONTEXT_TOKEN_KEY, token);
                super.start(responseListener, headers);
            }
        };
    }
}
