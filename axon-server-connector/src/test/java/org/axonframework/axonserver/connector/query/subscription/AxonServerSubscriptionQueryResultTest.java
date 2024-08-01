/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.SubscriptionQueryResult;
import io.axoniq.axonserver.connector.query.impl.SubscriptionQueryUpdateBuffer;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest;
import io.grpc.stub.ClientCallStreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.common.FutureUtils;
import org.axonframework.queryhandling.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

class AxonServerSubscriptionQueryResultTest {

    private ScheduledExecutorService executorService;

    private SubscriptionQueryUpdateBuffer subscriptionQueryUpdateBuffer;

    private AxonServerSubscriptionQueryResult<String, String> testSubject;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        executorService = Executors.newScheduledThreadPool(1);

        subscriptionQueryUpdateBuffer = new SubscriptionQueryUpdateBuffer("testClient", "queryId", 10, 3);
        SubscriptionQueryResult result = new SubscriptionQueryResult() {
            @Override
            public CompletableFuture<QueryResponse> initialResult() {
                return FutureUtils.emptyCompletedFuture();
            }

            @Override
            public ResultStream<QueryUpdate> updates() {
                return subscriptionQueryUpdateBuffer;
            }
        };
        ClientCallStreamObserver<SubscriptionQueryRequest> mockUpstream = mock(ClientCallStreamObserver.class);
        subscriptionQueryUpdateBuffer.beforeStart(mockUpstream);

        AxonServerConfiguration configuration = new AxonServerConfiguration();
        Serializer serializer = TestSerializer.xStreamSerializer();
        SubscriptionMessageSerializer testSerializer =
                new SubscriptionMessageSerializer(serializer, serializer, configuration);
        QueryBusSpanFactory noOpSpanFactory = DefaultQueryBusSpanFactory.builder()
                                                                        .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                        .build();
        testSubject = new AxonServerSubscriptionQueryResult<>(null,
                                                              result,
                                                              testSerializer,
                                                              noOpSpanFactory,
                                                              NoOpSpanFactory.NoOpSpan.INSTANCE);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }

    @Test
    void subscriptionQueryClosesUpdateFluxWithErrorOnErrorInResultStream() {
        executorService.schedule(
                () -> subscriptionQueryUpdateBuffer.onError(new RuntimeException("Test")), 10, TimeUnit.MILLISECONDS
        );

        StepVerifier.create(testSubject.updates())
                    .expectError(RuntimeException.class)
                    .verify(Duration.ofSeconds(1));
    }

    @Test
    void subscriptionQueryCompletesUpdateFluxOnCompletedResultStream() {
        executorService.schedule(() -> subscriptionQueryUpdateBuffer.onCompleted(), 10, TimeUnit.MILLISECONDS);

        StepVerifier.create(testSubject.updates())
                    .expectComplete()
                    .verify(Duration.ofSeconds(1));
    }
}
