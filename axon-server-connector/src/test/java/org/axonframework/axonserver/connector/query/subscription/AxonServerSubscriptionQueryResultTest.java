package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.SubscriptionQueryResult;
import io.axoniq.axonserver.connector.query.impl.SubscriptionQueryUpdateBuffer;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest;
import io.grpc.stub.ClientCallStreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

class AxonServerSubscriptionQueryResultTest {

    private AxonServerSubscriptionQueryResult<String, String> testSubject;
    private AxonServerConfiguration configuration;
    private Serializer serializer;
    private CompletableFuture<QueryResponse> initialResult;
    private SubscriptionQueryUpdateBuffer subscriptionQueryUpdateBuffer;
    private ClientCallStreamObserver<SubscriptionQueryRequest> mockUpstream;
    private ScheduledExecutorService executorService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        executorService = Executors.newScheduledThreadPool(1);

        configuration = new AxonServerConfiguration();
        serializer = XStreamSerializer.defaultSerializer();
        SubscriptionMessageSerializer stubSerializer = new SubscriptionMessageSerializer(serializer, serializer, configuration);
        subscriptionQueryUpdateBuffer = new SubscriptionQueryUpdateBuffer("testClient", "queryId", 10, 3);
        SubscriptionQueryResult result = new SubscriptionQueryResult() {
            @Override
            public CompletableFuture<QueryResponse> initialResult() {
                return initialResult;
            }

            @Override
            public ResultStream<QueryUpdate> updates() {
                return subscriptionQueryUpdateBuffer;
            }
        };
        mockUpstream = mock(ClientCallStreamObserver.class);
        subscriptionQueryUpdateBuffer.beforeStart(mockUpstream);
        testSubject = new AxonServerSubscriptionQueryResult<>(result, stubSerializer);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }

    @Test
    void testSubscriptionQueryClosesUpdateFluxWithErrorOnErrorInResultStream() {
        executorService.schedule(() -> {
            subscriptionQueryUpdateBuffer.onError(new RuntimeException("Test"));
        }, 10, TimeUnit.MILLISECONDS);

        StepVerifier.create(testSubject.updates())
                    .expectError(RuntimeException.class)
                    .verify(Duration.ofSeconds(1));
    }

    @Test
    void testSubscriptionQueryCompletesUpdateFluxOnCompletedResultStream() {
        executorService.schedule(() -> {
            subscriptionQueryUpdateBuffer.onCompleted();
        }, 10, TimeUnit.MILLISECONDS);

        StepVerifier.create(testSubject.updates())
                    .expectComplete()
                    .verify(Duration.ofSeconds(1));
    }
}