package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.axoniq.axonserver.grpc.event.GetAggregateSnapshotsRequest;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.TestStreamObserver;
import org.axonframework.axonserver.connector.command.DummyMessagePlatformServer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test class to verify the operations the {@link AxonServerEventStoreClient} might perform. Currently only
 * verifies whether all the {@link io.grpc.Channel}s created have the {@code context} passed to them.
 *
 * @author Steven van Beelen
 */
class AxonServerEventStoreClientTest {

    private static final String BOUNDED_CONTEXT = "not-important";

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConnectionManager axonServerConnectionManager;

    private AxonServerEventStoreClient testSubject;

    @BeforeEach
    void setUp() throws Exception {
        dummyMessagePlatformServer = new DummyMessagePlatformServer();
        dummyMessagePlatformServer.start();

        AxonServerConfiguration configuration = new AxonServerConfiguration();
        configuration.setServers(dummyMessagePlatformServer.getAddress());
        configuration.setClientId("JUnit");
        configuration.setComponentName("JUnit");
        configuration.setInitialNrOfPermits(100);
        configuration.setNewPermitsThreshold(10);
        configuration.setNrOfNewPermits(1000);
        configuration.setContext(BOUNDED_CONTEXT);
        axonServerConnectionManager = spy(AxonServerConnectionManager.builder()
                                                                     .axonServerConfiguration(configuration)
                                                                     .build());

        testSubject = new AxonServerEventStoreClient(configuration, axonServerConnectionManager);
    }

    @AfterEach
    void tearDown() {
        axonServerConnectionManager.shutdown();
        dummyMessagePlatformServer.stop();
    }

    @Test
    void testStopReadingEvents() throws InterruptedException {
        TestStreamObserver<EventWithToken> receivingStreamObserver = new TestStreamObserver<>();
        StreamObserver<Event> sendingStreamObserver = dummyMessagePlatformServer.eventStore()
                                                                                .appendEvent(new TestStreamObserver<>());
        StreamObserver<GetEventsRequest> requestEvents = testSubject.listEvents(BOUNDED_CONTEXT,
                                                                                receivingStreamObserver);
        sendingStreamObserver.onNext(Event.getDefaultInstance());
        sendingStreamObserver.onCompleted();
        requestEvents.onNext(GetEventsRequest.newBuilder().setNumberOfPermits(10).build());
        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertEquals(1, receivingStreamObserver.sentMessages().size()));

        testSubject.stopReadingEvents();

        sendingStreamObserver.onNext(Event.getDefaultInstance());
        sendingStreamObserver.onCompleted();
        requestEvents.onNext(GetEventsRequest.newBuilder().setNumberOfPermits(10).build());
        Thread.sleep(100);
        assertEquals(1, receivingStreamObserver.sentMessages().size());
    }

    @Test
    void testStopSendingEvents() throws InterruptedException, ExecutionException, TimeoutException {
        AppendEventTransaction appendEventConnection = testSubject.createAppendEventConnection(BOUNDED_CONTEXT);
        appendEventConnection.append(Event.getDefaultInstance());
        Executors.newScheduledThreadPool(1)
                 .schedule(() -> {
                     try {
                         appendEventConnection.commit();
                     } catch (Exception e) {
                         fail(e);
                     }
                 }, 100, TimeUnit.MILLISECONDS);
        testSubject.stopSendingEvents()
                   .get(1, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> appendEventConnection.append(Event.getDefaultInstance()));
        assertThrows(IllegalStateException.class, () -> testSubject.createAppendEventConnection(BOUNDED_CONTEXT));
    }

    @Test
    void testListAggregateEvents() {
        GetAggregateEventsRequest testRequest = GetAggregateEventsRequest.getDefaultInstance();

        testSubject.listAggregateEvents(testRequest);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testListAggregateEventsWithContext() {
        GetAggregateEventsRequest testRequest = GetAggregateEventsRequest.getDefaultInstance();

        testSubject.listAggregateEvents(BOUNDED_CONTEXT, testRequest);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testListEvents() {
        StreamObserver<EventWithToken> testStreamObserver = new TestStreamObserver<>();

        testSubject.listEvents(testStreamObserver);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testListEventsWithContext() {
        StreamObserver<EventWithToken> testStreamObserver = new TestStreamObserver<>();

        testSubject.listEvents(BOUNDED_CONTEXT, testStreamObserver);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testAppendSnapshot() {
        Event testSnapshotEvent = Event.getDefaultInstance();

        testSubject.appendSnapshot(testSnapshotEvent);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testAppendSnapshotWithContext() {
        Event testSnapshotEvent = Event.getDefaultInstance();

        testSubject.appendSnapshot(BOUNDED_CONTEXT, testSnapshotEvent);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetLastToken() {
        testSubject.getLastToken();

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetLastTokenWithContext() {
        testSubject.getLastToken(BOUNDED_CONTEXT);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetFirstToken() {
        testSubject.getFirstToken();

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetFirstTokenWithContext() {
        testSubject.getFirstToken(BOUNDED_CONTEXT);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetTokenAt() {
        Instant testInstant = Instant.now();

        testSubject.getTokenAt(testInstant);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testGetTokenAtWithContext() {
        Instant testInstant = Instant.now();

        testSubject.getTokenAt(BOUNDED_CONTEXT, testInstant);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testCreateAppendEventConnection() {
        testSubject.createAppendEventConnection();

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testCreateAppendEventConnectionWithContext() {
        testSubject.createAppendEventConnection(BOUNDED_CONTEXT);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testQuery() {
        StreamObserver<QueryEventsResponse> testSreamObserver = new TestStreamObserver<>();

        testSubject.query(testSreamObserver);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testQueryWithContext() {
        StreamObserver<QueryEventsResponse> testSreamObserver = new TestStreamObserver<>();

        testSubject.query(BOUNDED_CONTEXT, testSreamObserver);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testLastSequenceNumberFor() {
        String testAggregateId = "some-id";

        testSubject.lastSequenceNumberFor(testAggregateId);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testLastSequenceNumberForWithContext() {
        String testAggregateId = "some-id";

        testSubject.lastSequenceNumberFor(BOUNDED_CONTEXT, testAggregateId);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testListAggregateSnapshots() {
        GetAggregateSnapshotsRequest testRequest = GetAggregateSnapshotsRequest.getDefaultInstance();

        testSubject.listAggregateSnapshots(testRequest);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    void testListAggregateSnapshotsWithContext() {
        GetAggregateSnapshotsRequest testRequest = GetAggregateSnapshotsRequest.getDefaultInstance();

        testSubject.listAggregateSnapshots(BOUNDED_CONTEXT, testRequest);

        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }
}