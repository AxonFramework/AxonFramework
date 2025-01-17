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
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.serialization.Converter;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
                                                .routingServers(new ServerAddress(axonServerContainer.getHost(), axonServerContainer.getGrpcPort()))
                                                .build()
                                                .connect("default");
    }

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        axonServerContainer.stop();
    }

    @Test
    void eventWithMultipleTagsIsReportedAsPartOfException() {
        TaggedEventMessage<?> violatingEntry = taggedEventMessage("event2",
                                                                  Set.of(new Tag("key1", "value1"),
                                                                         new Tag("key2", "value2")));
        CompletableFuture<AsyncEventStorageEngine.AppendTransaction> actual = testSubject.appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event1", Set.of(new Tag("key1", "value1"))),
                violatingEntry,
                taggedEventMessage("event3", Set.of(new Tag("key1", "value1")))
        );

        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());

        ExecutionException actualException = assertThrows(ExecutionException.class, actual::get);
        if (actualException.getCause() instanceof TooManyTagsOnEventMessageException e) {
            assertEquals(violatingEntry.tags(), e.tags());
            assertEquals(violatingEntry.event(), e.eventMessage());
        } else {
            fail("Unexpected exception", actualException);
        }
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
    protected long sequenceOfEventNo(long eventNo) {
        return eventNo - 1;
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return original.withConvertedPayload(p -> new String((byte[]) p, StandardCharsets.UTF_8));
    }
}