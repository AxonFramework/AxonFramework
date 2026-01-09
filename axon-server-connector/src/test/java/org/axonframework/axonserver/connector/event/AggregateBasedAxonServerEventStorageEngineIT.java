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
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tags({
        @Tag("slow"),
})
class AggregateBasedAxonServerEventStorageEngineIT extends
        AggregateBasedStorageEngineTestSuite<AggregateBasedAxonServerEventStorageEngine> {

    @SuppressWarnings("resource")
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
                () -> testSubject.stream(StreamingCondition.startingFrom(
                        new GapAwareTrackingToken(5, Collections.emptySet())
                ))
        );
    }

    @Override
    protected AggregateBasedAxonServerEventStorageEngine buildStorageEngine() throws IOException {
        AxonServerContainerUtils.purgeEventsFromAxonServer(axonServerContainer.getHost(),
                                                           axonServerContainer.getHttpPort(),
                                                           "default",
                                                           AxonServerContainerUtils.NO_DCB_CONTEXT);
        return new AggregateBasedAxonServerEventStorageEngine(connection, converter);
    }

    @Override
    protected ProcessingContext processingContext() {
        return null;
    }

    @Override
    protected long globalSequenceOfEvent(long position) {
        return position - 1;
    }

    @Override
    protected TrackingToken trackingTokenAt(long position) {
        return new GlobalSequenceTrackingToken(globalSequenceOfEvent(position));
    }

    @Override
    protected EventMessage convertPayload(EventMessage original) {
        return original.withConvertedPayload(String.class, converter);
    }

    @Test
    void transactionCanBeCommitedOnlyOnce() {
        var tx =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                         processingContext(),
                                         taggedEventMessage("event-0", TEST_AGGREGATE_TAGS)).join();

        assertDoesNotThrow(() -> tx.commit().get(1, TimeUnit.SECONDS));
        assertThrows(Exception.class, () -> tx.commit().get(1, TimeUnit.SECONDS));
    }
}