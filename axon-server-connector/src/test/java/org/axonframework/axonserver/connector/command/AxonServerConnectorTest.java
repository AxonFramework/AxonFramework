/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.distributed.SerializingConnector;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

class AxonServerConnectorTest {

    AxonServerContainer axonServerContainer = new AxonServerContainer();
    private Serializer serializer;
    private SerializingConnector<byte[]> connector;

    @Test
    void name() throws InterruptedException {
        serializer = JacksonSerializer.defaultSerializer();
        axonServerContainer.start();
        AxonServerConnection connection = AxonServerConnectionFactory.forClient("test")
                                                                     .routingServers(new ServerAddress(
                                                                             axonServerContainer.getHost(),
                                                                             axonServerContainer.getGrpcPort()))
                                                                     .build().connect(
                        "default");

        connector = new SerializingConnector<>(new AxonServerConnector(connection.commandChannel()), serializer, byte[].class);

        connector.onIncomingCommand((message, callback) -> {
            System.out.println(new String((byte[])message.getPayload()));
            callback.success(new GenericCommandResultMessage<>((Object) null));
        });
        connector.subscribe("test", 100);

        Thread.sleep(100);

        CompletableFuture<? extends CommandResultMessage<?>> result = connector.dispatch(
                new GenericCommandMessage<>(new GenericMessage<>("Testing 123"), "test"),
                ProcessingContext.NONE);

        result.whenComplete((r, e) -> {
            if (e == null) {
                System.out.println("Result: " + r);
            } else {
                e.printStackTrace();
            }
        }).join();
    }
}