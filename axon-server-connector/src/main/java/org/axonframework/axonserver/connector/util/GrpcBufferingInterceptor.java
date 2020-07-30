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
 * Interceptor that immediately requests a number of messages from the server, to increase the flow of messages.
 * <p>
 * As an additional message is requested by default each time a message is received, setting an initial request amount
 * will allow that number of messages to be "in transit" before the server stops sending more.
 *
 * @author Allard Buijze
 * @since 4.2
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class GrpcBufferingInterceptor implements ClientInterceptor {

    private final int additionalBuffer;

    /**
     * Initialize the interceptor to ask for {@code additionalBuffer} amount of messages from the server.
     *
     * @param additionalBuffer The number of messages the server may send before waiting for permits to be renewed
     */
    public GrpcBufferingInterceptor(int additionalBuffer) {
        this.additionalBuffer = additionalBuffer;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
        if (additionalBuffer == 0 || method.getType().serverSendsOneMessage()) {
            return call;
        }
        return new AdditionalMessageRequestingCall<>(call, additionalBuffer);
    }

    private static class AdditionalMessageRequestingCall<ReqT, RespT>
            extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {

        private final int additionalBuffer;

        public AdditionalMessageRequestingCall(ClientCall<ReqT, RespT> call, int additionalBuffer) {
            super(call);
            this.additionalBuffer = additionalBuffer;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            super.start(responseListener, headers);
            request(additionalBuffer);
        }
    }
}
