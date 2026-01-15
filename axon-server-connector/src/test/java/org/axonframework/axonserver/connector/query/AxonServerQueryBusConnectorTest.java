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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.connector.query.SubscriptionQueryResult;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.util.StubResultStream;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AxonServerQueryBusConnectorTest {

    private final String clientId = "clientId";
    private final String componentName = "componentName";

    private final AxonServerConnection connection = mock(AxonServerConnection.class);
    private final QueryChannel mockQueryChannel = mock(QueryChannel.class);
    private final AxonServerConfiguration configuration = AxonServerConfiguration.builder()
                                                                                 .clientId(clientId)
                                                                                 .componentName(componentName)
                                                                                 .build();

    private final AxonServerQueryBusConnector testSubject = new AxonServerQueryBusConnector(connection, configuration);

    @BeforeEach
    void setUp() {
        when(connection.queryChannel()).thenReturn(mockQueryChannel);
        testSubject.start();
    }

    @Nested
    class SubscribeUnsubscribe {

        @Test
        void subscribeRegistersQueryHandler() {
            Registration reg = mock(Registration.class);
            when(reg.onAck(any(Runnable.class))).thenAnswer(i -> {
                i.getArgument(0, Runnable.class).run();
                return null;
            });
            when(mockQueryChannel.registerQueryHandler(any(), any(QueryDefinition.class))).thenReturn(reg);

            CompletableFuture<Void> future = testSubject.subscribe(new QualifiedName("TestQuery"));

            assertThat(future).isCompleted();
            verify(mockQueryChannel).registerQueryHandler(any(QueryHandler.class), any(QueryDefinition.class));
        }

        @Test
        void unsubscribeCancelsRegistrationAndReturnsTrueWhenPresent() {
            Registration reg = mock(Registration.class);
            when(reg.onAck(any(Runnable.class))).thenAnswer(i -> {
                i.getArgument(0, Runnable.class).run();
                return null;
            });
            when(mockQueryChannel.registerQueryHandler(any(), any(QueryDefinition.class))).thenReturn(reg);
            QualifiedName name = new QualifiedName("TestQuery");
            testSubject.subscribe(name).join();

            boolean result = testSubject.unsubscribe(name);

            assertThat(result).isTrue();
            verify(reg).cancel();
        }

        @Test
        void unsubscribeReturnsFalseWhenNotPresent() {
            boolean result = testSubject.unsubscribe(new QualifiedName("TestQuery"));
            assertThat(result).isFalse();
        }
    }

    @Nested
    class QueryDispatching {

        @Test
        void queryWithErrorAsFirstMessageReturnsNoAvailableMessageAndSetsError() {
            // given
            QueryResponse errorResponse = QueryResponse.newBuilder()
                                                       .setMessageIdentifier(UUID.randomUUID().toString())
                                                       .setErrorCode("AXONIQ-1000")
                                                       .setErrorMessage(io.axoniq.axonserver.grpc.ErrorMessage.newBuilder()
                                                                                                              .setMessage("Query execution failed")
                                                                                                              .build())
                                                       .build();
            ResultStream<QueryResponse> resultStream = new StubResultStream<>(errorResponse);
            when(mockQueryChannel.query(any())).thenReturn(resultStream);

            QueryMessage query = new GenericQueryMessage(
                    new GenericMessage(new MessageType("QueryType", "1"),
                                       "payload".getBytes(),
                                       Metadata.emptyInstance())
            );

            // when
            MessageStream<QueryResponseMessage> stream = testSubject.query(query, null);

            // then
            assertThat(stream.error()).isPresent();
            assertThat(stream.error().get()).hasMessageContaining("Query execution failed");
            assertThat(stream.isCompleted()).isTrue();
            assertThat(stream.hasNextAvailable()).isFalse();
        }

        @Test
        void queryDelegatesToQueryChannelAndConvertsResponseAndClosesOnClose() {
            // Prepare a simple response stream with one response
            QueryResponse response = QueryResponse.newBuilder()
                                                  .setMessageIdentifier(UUID.randomUUID().toString())
                                                  .setPayload(SerializedObject.newBuilder()
                                                                              .setType("java.lang.String")
                                                                              .setRevision("1")
                                                                              .setData(copyFromUtf8("ok"))
                                                                              .build())
                                                  .build();
            ResultStream<QueryResponse> resultStream = spy(new StubResultStream<>(response));
            when(mockQueryChannel.query(any())).thenReturn(resultStream);

            QueryMessage query = new GenericQueryMessage(
                    new GenericMessage(new MessageType("QueryType", "1"),
                                       "payload".getBytes(),
                                       Metadata.emptyInstance())
            );

            MessageStream<QueryResponseMessage> stream = testSubject.query(query, null);

            // Consume one entry
            Optional<MessageStream.Entry<QueryResponseMessage>> next = stream.next();
            assertThat(next).isPresent();
            assertThat(next.get().message().payloadAs(byte[].class)).isEqualTo("ok".getBytes());

            // Closing the stream should close the underlying ResultStream
            stream.close();
            verify(resultStream).close();
        }
    }

    @Nested
    class SubscriptionQueryDispatching {

        @Test
        void subscriptionQueryDelegatesToQueryChannelWithCalculatedBufferSegmentAndEmitsInitialAndUpdates() {
            // Given a subscription result with one initial result and two update
            String initialPayloadId = UUID.randomUUID().toString();
            QueryResponse initial = QueryResponse.newBuilder()
                                                 .setMessageIdentifier(initialPayloadId)
                                                 .setPayload(SerializedObject.newBuilder()
                                                                             .setType("java.lang.String")
                                                                             .setRevision("1")
                                                                             .setData(copyFromUtf8(
                                                                                     "result"))
                                                                             .build())
                                                 .build();
            StubResultStream<QueryUpdate> updates = new StubResultStream<>(
                    QueryUpdate.newBuilder().setMessageIdentifier(UUID.randomUUID().toString())
                               .setPayload(SerializedObject.newBuilder()
                                                           .setType("java.lang.String")
                                                           .setRevision("1")
                                                           .setData(copyFromUtf8("u1"))
                                                           .build()).build(),
                    QueryUpdate.newBuilder().setMessageIdentifier(UUID.randomUUID().toString())
                               .setPayload(SerializedObject.newBuilder()
                                                           .setType("java.lang.String")
                                                           .setRevision("1")
                                                           .setData(copyFromUtf8("u2"))
                                                           .build()).build()
            );
            SimpleSubscriptionQueryResult sqr = new SimpleSubscriptionQueryResult(initial, updates);
            when(mockQueryChannel.subscriptionQuery(any(), anyInt(), anyInt())).thenReturn(sqr);

            // Build a subscription query message
            QueryMessage query = new GenericQueryMessage(
                    new GenericMessage(new MessageType("QueryType", "1"),
                                       "payload".getBytes(),
                                       Metadata.emptyInstance())
            );
            QueryMessage sqm = new GenericQueryMessage(
                    query,
                    1
            );

            int updateBufferSize = 40;
            MessageStream<QueryResponseMessage> responses = testSubject.subscriptionQuery(sqm, null, updateBufferSize);

            // Verify buffer segment calculation: min(updateBufferSize/4, 8) -> min(10, 8) = 8
            verify(mockQueryChannel).subscriptionQuery(any(),
                                                       eq(updateBufferSize),
                                                       eq(8));

            // First entry is the initial result
            Optional<MessageStream.Entry<QueryResponseMessage>> first = responses.next();
            assertThat(first).isPresent();
            assertThat(first.get().message().payloadAs(byte[].class)).isEqualTo("result".getBytes());

            // Then update
            Optional<MessageStream.Entry<QueryResponseMessage>> second = responses.next();
            Optional<MessageStream.Entry<QueryResponseMessage>> third = responses.next();
            assertThat(second).isPresent();
            assertThat(third).isPresent();
            assertThat(second.get().message().payloadAs(byte[].class)).isEqualTo("u1".getBytes());
            assertThat(third.get().message().payloadAs(byte[].class)).isEqualTo("u2".getBytes());

            responses.close();
        }
    }


    // ---- Test support classes ----


    private static class SimpleSubscriptionQueryResult implements SubscriptionQueryResult {

        private final CompletableFuture<QueryResponse> initialFuture;
        private final StubResultStream<QueryUpdate> updates;
        private final StubResultStream<QueryResponse> initialStream;

        SimpleSubscriptionQueryResult(QueryResponse initial, StubResultStream<QueryUpdate> updates) {
            this.initialFuture = CompletableFuture.completedFuture(initial);
            this.initialStream = new StubResultStream<>(initial);
            this.updates = updates;
        }

        @Override
        public CompletableFuture<QueryResponse> initialResult() {
            return initialFuture;
        }

        @Override
        public ResultStream<QueryResponse> initialResults() {
            return initialStream;
        }

        @Override
        public ResultStream<QueryUpdate> updates() {
            return updates;
        }
    }
}