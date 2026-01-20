/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.CommandSubscription;
import io.axoniq.axonserver.grpc.control.QuerySubscription;
import io.axoniq.axonserver.grpc.control.UpdateType;
import org.junit.jupiter.api.*;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link TopologyChange}.
 *
 * @author Steven van Beelen
 */
class TopologyChangeTest {

    private static final String TEST_CONTEXT = "context";
    private static final String TEST_CLIENT_ID = "clientId";
    private static final String TEST_CLIENT_STREAM_ID = "clientStreamId";
    private static final String TEST_COMPONENT_NAME = "componentName";
    private static final String TEST_COMMAND_NAME = "commandName";
    private static final int TEST_LOAD_FACTOR = 42;
    private static final String TEST_QUERY_NAME = "queryName";

    @Test
    void mapsGrpcBasedAddCommandChange() {
        CommandSubscription commandSubscription = CommandSubscription.newBuilder()
                                                                     .setName(TEST_COMMAND_NAME)
                                                                     .setLoadFactor(TEST_LOAD_FACTOR)
                                                                     .build();
        io.axoniq.axonserver.grpc.control.TopologyChange grpcBasedChange =
                io.axoniq.axonserver.grpc.control.TopologyChange.newBuilder()
                                                                .setUpdateType(UpdateType.ADD_COMMAND_HANDLER)
                                                                .setContext(TEST_CONTEXT)
                                                                .setClientId(TEST_CLIENT_ID)
                                                                .setClientStreamId(TEST_CLIENT_STREAM_ID)
                                                                .setComponentName(TEST_COMPONENT_NAME)
                                                                .setCommand(commandSubscription)
                                                                .build();

        TopologyChange testSubject = new TopologyChange(grpcBasedChange);
        System.out.println(testSubject);

        assertEquals(TopologyChange.Type.COMMAND_HANDLER_ADDED, testSubject.type());
        assertEquals(TEST_CONTEXT, testSubject.context());
        assertEquals(TEST_CLIENT_ID, testSubject.clientId());
        assertEquals(TEST_CLIENT_STREAM_ID, testSubject.clientStreamId());
        assertEquals(TEST_COMPONENT_NAME, testSubject.componentName());
        TopologyChange.HandlerSubscription handlerSubscription = testSubject.handler();
        assertNotNull(handlerSubscription);
        assertEquals(TEST_COMMAND_NAME, handlerSubscription.name());
        OptionalInt optionalLoadFactor = handlerSubscription.loadFactor();
        assertTrue(optionalLoadFactor.isPresent());
        assertEquals(TEST_LOAD_FACTOR, optionalLoadFactor.getAsInt());
    }

    @Test
    void mapsGrpcBasedRemoveCommandChange() {
        CommandSubscription commandSubscription = CommandSubscription.newBuilder()
                                                                     .setName(TEST_COMMAND_NAME)
                                                                     .setLoadFactor(TEST_LOAD_FACTOR)
                                                                     .build();
        io.axoniq.axonserver.grpc.control.TopologyChange grpcBasedChange =
                io.axoniq.axonserver.grpc.control.TopologyChange.newBuilder()
                                                                .setUpdateType(UpdateType.REMOVE_COMMAND_HANDLER)
                                                                .setContext(TEST_CONTEXT)
                                                                .setClientId(TEST_CLIENT_ID)
                                                                .setClientStreamId(TEST_CLIENT_STREAM_ID)
                                                                .setComponentName(TEST_COMPONENT_NAME)
                                                                .setCommand(commandSubscription)
                                                                .build();

        TopologyChange testSubject = new TopologyChange(grpcBasedChange);

        assertEquals(TopologyChange.Type.COMMAND_HANDLER_REMOVED, testSubject.type());
        assertEquals(TEST_CONTEXT, testSubject.context());
        assertEquals(TEST_CLIENT_ID, testSubject.clientId());
        assertEquals(TEST_CLIENT_STREAM_ID, testSubject.clientStreamId());
        assertEquals(TEST_COMPONENT_NAME, testSubject.componentName());
        TopologyChange.HandlerSubscription handlerSubscription = testSubject.handler();
        assertNotNull(handlerSubscription);
        assertEquals(TEST_COMMAND_NAME, handlerSubscription.name());
        OptionalInt optionalLoadFactor = handlerSubscription.loadFactor();
        assertTrue(optionalLoadFactor.isPresent());
        assertEquals(TEST_LOAD_FACTOR, optionalLoadFactor.getAsInt());
    }

    @Test
    void mapsGrpcBasedAddQueryChange() {
        QuerySubscription querySubscription = QuerySubscription.newBuilder()
                                                               .setName(TEST_QUERY_NAME)
                                                               .build();
        io.axoniq.axonserver.grpc.control.TopologyChange grpcBasedChange =
                io.axoniq.axonserver.grpc.control.TopologyChange.newBuilder()
                                                                .setUpdateType(UpdateType.ADD_QUERY_HANDLER)
                                                                .setContext(TEST_CONTEXT)
                                                                .setClientId(TEST_CLIENT_ID)
                                                                .setClientStreamId(TEST_CLIENT_STREAM_ID)
                                                                .setComponentName(TEST_COMPONENT_NAME)
                                                                .setQuery(querySubscription)
                                                                .build();

        TopologyChange testSubject = new TopologyChange(grpcBasedChange);

        assertEquals(TopologyChange.Type.QUERY_HANDLER_ADDED, testSubject.type());
        assertEquals(TEST_CONTEXT, testSubject.context());
        assertEquals(TEST_CLIENT_ID, testSubject.clientId());
        assertEquals(TEST_CLIENT_STREAM_ID, testSubject.clientStreamId());
        assertEquals(TEST_COMPONENT_NAME, testSubject.componentName());
        TopologyChange.HandlerSubscription handlerSubscription = testSubject.handler();
        assertNotNull(handlerSubscription);
        assertEquals(TEST_QUERY_NAME, handlerSubscription.name());
        assertFalse(handlerSubscription.loadFactor().isPresent());
    }

    @Test
    void mapsGrpcBasedRemoveQueryChange() {
        QuerySubscription querySubscription = QuerySubscription.newBuilder()
                                                               .setName(TEST_QUERY_NAME)
                                                               .build();
        io.axoniq.axonserver.grpc.control.TopologyChange grpcBasedChange =
                io.axoniq.axonserver.grpc.control.TopologyChange.newBuilder()
                                                                .setUpdateType(UpdateType.REMOVE_QUERY_HANDLER)
                                                                .setContext(TEST_CONTEXT)
                                                                .setClientId(TEST_CLIENT_ID)
                                                                .setClientStreamId(TEST_CLIENT_STREAM_ID)
                                                                .setComponentName(TEST_COMPONENT_NAME)
                                                                .setQuery(querySubscription)
                                                                .build();

        TopologyChange testSubject = new TopologyChange(grpcBasedChange);

        assertEquals(TopologyChange.Type.QUERY_HANDLER_REMOVED, testSubject.type());
        assertEquals(TEST_CONTEXT, testSubject.context());
        assertEquals(TEST_CLIENT_ID, testSubject.clientId());
        assertEquals(TEST_CLIENT_STREAM_ID, testSubject.clientStreamId());
        assertEquals(TEST_COMPONENT_NAME, testSubject.componentName());
        TopologyChange.HandlerSubscription handlerSubscription = testSubject.handler();
        assertNotNull(handlerSubscription);
        assertEquals(TEST_QUERY_NAME, handlerSubscription.name());
        assertFalse(handlerSubscription.loadFactor().isPresent());
    }

    @Test
    void mapsGrpcBasedResetChange() {
        io.axoniq.axonserver.grpc.control.TopologyChange grpcBasedChange =
                io.axoniq.axonserver.grpc.control.TopologyChange.newBuilder()
                                                                .setUpdateType(UpdateType.RESET_ALL)
                                                                .setContext(TEST_CONTEXT)
                                                                .setClientId(TEST_CLIENT_ID)
                                                                .setClientStreamId(TEST_CLIENT_STREAM_ID)
                                                                .setComponentName(TEST_COMPONENT_NAME)
                                                                .build();


        TopologyChange testSubject = new TopologyChange(grpcBasedChange);

        assertEquals(TopologyChange.Type.RESET, testSubject.type());
        assertEquals(TEST_CONTEXT, testSubject.context());
        assertEquals(TEST_CLIENT_ID, testSubject.clientId());
        assertEquals(TEST_CLIENT_STREAM_ID, testSubject.clientStreamId());
        assertEquals(TEST_COMPONENT_NAME, testSubject.componentName());
        assertNull(testSubject.handler());
    }
}