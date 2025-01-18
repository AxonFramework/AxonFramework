/*
 * Copyright (c) 2010-2025. Axon Framework
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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.serialization.Converter;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class LegacyAxonServerEventStorageEngineTest extends
        AggregateBasedStorageEngineTestSuite<LegacyAxonServerEventStorageEngine> {

    private static final AxonServerContainer axonServerContainer = new AxonServerContainer()
            .withAxonServerHostname("localhost")
            .withDevMode(true);
    private static AxonServerConnection connection;

    @BeforeAll
    static void beforeAll() {
        axonServerContainer.start();
        connection = AxonServerConnectionFactory.forClient("Test")
                                                .routingServers(new ServerAddress(axonServerContainer.getHost(),
                                                                                  axonServerContainer.getGrpcPort()))
                                                .build()
                                                .connect("default");
    }

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        axonServerContainer.stop();
    }

    @Test
    void sourcingFromNonGlobalSequenceTrackingTokenShouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.stream(StreamingCondition.startingFrom(new GapAwareTrackingToken(5,
                                                                                                   Collections.emptySet())))
        );
    }

    @Override
    protected LegacyAxonServerEventStorageEngine buildStorageEngine() throws IOException {
        AxonServerUtils.purgeEventsFromAxonServer(axonServerContainer.getHost(),
                                                  axonServerContainer.getHttpPort(),
                                                  "default");
        return new LegacyAxonServerEventStorageEngine(connection, new Converter() {
            @Override
            public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
                return byte[].class.isAssignableFrom(targetType);
            }

            @Override
            public <T> T convert(Object original, Class<?> sourceType, Class<T> targetType) {
                return (T) original.toString().getBytes(StandardCharsets.UTF_8);
            }
        });
    }

    @Override
    protected long globalSequenceOfEvent(long eventNumber) {
        return eventNumber - 1;
    }

    @Override
    protected TrackingToken trackingTokenOnPosition(long eventNumber) {
        return new GlobalSequenceTrackingToken(globalSequenceOfEvent(eventNumber));
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return original.withConvertedPayload(p -> new String((byte[]) p, StandardCharsets.UTF_8));
    }
}