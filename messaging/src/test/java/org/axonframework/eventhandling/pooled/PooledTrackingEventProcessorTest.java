package org.axonframework.eventhandling.pooled;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PooledTrackingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class PooledTrackingEventProcessorTest {

    private static final String PROCESSOR_NAME = "test";

    private PooledTrackingEventProcessor testSubject;
    private EventHandlerInvoker stubEventHandler;
    private InMemoryMessageSource stubMessageSource;
    private InMemoryTokenStore tokenStore;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;

    @BeforeEach
    void setUp() {
        stubMessageSource = new InMemoryMessageSource();
        stubEventHandler = mock(EventHandlerInvoker.class);
        tokenStore = new InMemoryTokenStore();
        coordinatorExecutor = Executors.newScheduledThreadPool(1);
        workerExecutor = Executors.newScheduledThreadPool(8);

        testSubject = PooledTrackingEventProcessor.builder()
                                                  .name(PROCESSOR_NAME)
                                                  .eventHandlerInvoker(stubEventHandler)
                                                  .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                                                  .errorHandler(PropagatingErrorHandler.instance())
                                                  .messageSource(stubMessageSource)
                                                  .tokenStore(tokenStore)
                                                  .transactionManager(NoTransactionManager.instance())
                                                  .coordinatorExecutor(name -> coordinatorExecutor)
                                                  .workerExecutorService(name -> workerExecutor)
                                                  .initialSegmentCount(8)
                                                  .claimExtensionThreshold(1000)
                                                  .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        coordinatorExecutor.shutdown();
        workerExecutor.shutdown();
    }

    @Test
    void testStartingProcessorClaimsAllAvailableTokens() {
        List<EventMessage<Integer>> events = IntStream.range(0, 100)
                                                      .mapToObj(GenericEventMessage::new)
                                                      .collect(Collectors.toList());
        events.forEach(stubMessageSource::publishMessage);
        mockEventHandlerInvoker();

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            long nonNullTokens = IntStream.range(0, 8)
                                          .mapToObj(i -> tokenStore.fetchToken(PROCESSOR_NAME, i))
                                          .filter(Objects::nonNull)
                                          .count();
            assertEquals(8, nonNullTokens);
        });
        assertEquals(8, testSubject.processingStatus().size());
    }

    @Test
    void testAllTokensUpdatedToLatestValue() {
        List<EventMessage<Integer>> events = IntStream.range(0, 100)
                                                      .mapToObj(GenericEventMessage::new)
                                                      .collect(Collectors.toList());
        events.forEach(stubMessageSource::publishMessage);
        mockEventHandlerInvoker();

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(6, TimeUnit.SECONDS, () -> {
            long lowestToken = IntStream.range(0, 8)
                                        .mapToObj(i -> tokenStore.fetchToken(testSubject.getName(), i))
                                        .mapToLong(this::tokenPosition)
                                        .min()
                                        .orElse(-1);
            assertEquals(100, lowestToken);
        });
    }

    private long tokenPosition(TrackingToken token) {
        return token == null ? 0 : token.position().orElseThrow(IllegalArgumentException::new);
    }

    @Test
    void testExceptionWhileHandlingEventAbortsWorker() throws Exception {
        List<EventMessage<Integer>> events = Stream.of(1, 2, 2, 4, 5)
                                                   .map(GenericEventMessage::new)
                                                   .collect(Collectors.toList());
        mockEventHandlerInvoker();
        doThrow(new RuntimeException("Simulating worker failure"))
                .doNothing()
                .when(stubEventHandler)
                .handle(argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())), any());

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertEquals(8, tokenStore.fetchSegments(PROCESSOR_NAME).length);

        verify(stubEventHandler, never()).canHandle(any(), any());

        events.forEach(e -> stubMessageSource.publishMessage(e));

        assertWithin(1, TimeUnit.SECONDS, () -> {
            try {
                verify(stubEventHandler).handle(
                        argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())),
                        argThat(s -> s.getSegmentId() == events.get(2).getPayload())
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertEquals(7, testSubject.processingStatus().size());
            assertFalse(testSubject.processingStatus().containsKey(2));
        });
    }

    @Test
    void testStatusIsUpdatedWithTrackingToken() {
        testSubject.start();

        Stream.of(1, 2, 2, 4, 5)
              .map(GenericEventMessage::new)
              .forEach(stubMessageSource::publishMessage);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> testSubject.processingStatus().values().forEach(
                        status -> assertEquals(5, status.getCurrentPosition().orElse(0))
                )
        );
    }

    @Test
    void testShutdownCompletesAfterAbortingWorkPackages()
            throws InterruptedException, ExecutionException, TimeoutException {
        testSubject.start();
        Stream.of(1, 2, 2, 4, 5).map(GenericEventMessage::new).forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        testSubject.shutdownAsync().get(1, TimeUnit.SECONDS);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.processingStatus().size()));

        assertFalse(coordinatorExecutor.isShutdown());
        assertFalse(workerExecutor.isShutdown());
    }

    @Test
    void testShutdownProcessorWhichHasNotStartedYetReturnsCompletedFuture() {
        assertTrue(testSubject.shutdownAsync().isDone());
    }

    @Test
    void testShutdownProcessorAsyncTwiceReturnsSameFuture() {
        testSubject.start();

        CompletableFuture<Void> resultOne = testSubject.shutdownAsync();
        CompletableFuture<Void> resultTwo = testSubject.shutdownAsync();

        assertSame(resultOne, resultTwo);
    }

    @Test
    void testStartFailsWhenShutdownIsInProgress() throws Exception {
        // Use CountDownLatch to block worker threads from actually doing work, and thus shutting down successfully.
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(i -> {
            latch.await();
            return i.callRealMethod();
        }).when(stubEventHandler)
          .handle(any(), any());

        testSubject.start();

        Stream.of(1, 2, 2, 4, 5)
              .map(GenericEventMessage::new)
              .forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        CompletableFuture<Void> shutdownComplete = testSubject.shutdownAsync();
        assertThrows(IllegalStateException.class, () -> testSubject.start());
        // Unblock the Worker threads
        latch.countDown();
        shutdownComplete.get(1, TimeUnit.SECONDS);

        // This is allowed
        assertDoesNotThrow(() -> testSubject.start());
    }


    private void mockEventHandlerInvoker() {
        when(stubEventHandler.canHandle(any(), any())).thenAnswer(
                answer -> answer.getArgument(0, EventMessage.class)
                                .getPayload()
                                .equals(answer.getArgument(1, Segment.class).getSegmentId())
        );
        when(stubEventHandler.canHandleType(any())).thenReturn(true);
    }

    private static class InMemoryMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

        private final List<TrackedEventMessage<?>> messages = new CopyOnWriteArrayList<>();

        @Override
        public BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
            return new BlockingStream<TrackedEventMessage<?>>() {

                private int lastToken;

                @Override
                public Optional<TrackedEventMessage<?>> peek() {
                    if (messages.size() > lastToken) {
                        return Optional.of(messages.get(lastToken));
                    }
                    return Optional.empty();
                }

                @Override
                public boolean hasNextAvailable(int timeout, TimeUnit unit) {
                    return peek().isPresent();
                }

                @Override
                public TrackedEventMessage<?> nextAvailable() {
                    TrackedEventMessage<?> next = peek().orElseThrow(
                            () -> new RuntimeException("The processor should never perform a blocking call")
                    );
                    this.lastToken = (int) next.trackingToken()
                                               .position()
                                               .orElseThrow(() -> new UnsupportedOperationException("Not supported"));
                    return next;
                }

                @Override
                public void close() {
                    // doesn't matter.
                }
            };
        }

        @Override
        public TrackingToken createTailToken() {
            return null;
        }

        @Override
        public TrackingToken createHeadToken() {
            if (messages.isEmpty()) {
                return null;
            }
            return messages.get(messages.size() - 1).trackingToken();
        }

        @Override
        public TrackingToken createTokenAt(Instant dateTime) {
            return null;
        }

        @Override
        public TrackingToken createTokenSince(Duration duration) {
            return null;
        }

        public synchronized void publishMessage(EventMessage<?> message) {
            int nextToken = messages.size();
            messages.add(new GenericTrackedEventMessage<>(new GlobalSequenceTrackingToken(nextToken + 1), message));
        }
    }
}