/*
 * Copyright (c) 2018-2022. Axon Framework
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

package org.axonframework.axonserver.connector.event;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.netty.NettyServerBuilder;
import org.axonframework.axonserver.connector.PlatformService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class StubServer {

    private final Server server;
    private final PlatformService platformService;
    private int port;

    public StubServer(int port) {
        this(port, port);
    }

    public StubServer(int port, int redirectPort) {
        this(port, new PlatformService(redirectPort));
    }

    public StubServer(int port, PlatformService platformService) {
        this(port, platformService, new EventStoreImpl());
    }

    public StubServer(int port, PlatformService platformService, EventStoreImpl eventStore) {
        this.port = port;
        server = NettyServerBuilder.forPort(port)
                                   .addService(eventStore)
                                   .addService(platformService)
                                   .intercept(new ContextInterceptor())
                                   .build();
        this.platformService = platformService;
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() throws InterruptedException {
        server.shutdown();
        server.awaitTermination(1, TimeUnit.SECONDS);
    }

    public int getPort() {
        return port;
    }

    public PlatformService getPlatformService() {
        return platformService;
    }

    public static class ContextInterceptor implements ServerInterceptor {
        @Override
        public <T, R> ServerCall.Listener<T> interceptCall(ServerCall<T, R> serverCall, Metadata metadata, ServerCallHandler<T, R> serverCallHandler) {
            String context = metadata.get(PlatformService.AXON_IQ_CONTEXT);
            if (context == null) {
                context = "default";
            }
            Context updatedGrpcContext = Context.current().withValue(PlatformService.CONTEXT_KEY, context);
            return Contexts.interceptCall(updatedGrpcContext, serverCall, metadata, serverCallHandler);
        }
    }
}
