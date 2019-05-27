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

package org.axonframework.axonserver.connector.event;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.axonframework.axonserver.connector.PlatformService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class StubServer {

    private final Server server;
    private final PlatformService platformService;

    public StubServer(int port) {
        this(port, port);
    }

    public StubServer(int port, int redirectPort) {
        platformService = new PlatformService(redirectPort);
        server = NettyServerBuilder.forPort(port)
                                   .addService(new EventStoreImpl())
                                   .addService(platformService)
                                   .build();
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() throws InterruptedException {
        server.shutdown();
        server.awaitTermination(1, TimeUnit.SECONDS);
    }

    public PlatformService getPlatformService() {
        return platformService;
    }
}
