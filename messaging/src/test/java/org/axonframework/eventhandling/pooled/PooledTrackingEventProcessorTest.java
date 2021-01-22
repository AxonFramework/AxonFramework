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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PooledTrackingEventProcessorTest {

    private PooledTrackingEventProcessor testSubject;
    private EventHandlerInvoker stubEventHandler;
    private InMemoryMessageSource stubMessageSource;
    private InMemoryTokenStore tokenStore;
    private ScheduledExecutorService executorService;

    @BeforeEach
    void setUp() {
        stubMessageSource = new InMemoryMessageSource();
        stubEventHandler = mock(EventHandlerInvoker.class);
        tokenStore = new InMemoryTokenStore();
        executorService = Executors.newScheduledThreadPool(2);
        testSubject = PooledTrackingEventProcessor.builder().name("test")
                                                  .errorHandler(PropagatingErrorHandler.instance())
                                                  .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                                                  .tokenStore(tokenStore)
                                                  .eventHandlerInvoker(stubEventHandler)
                                                  .messageSource(stubMessageSource)
                                                  .transactionManager(NoTransactionManager.instance())
                                                  .coordinatorExecutor(executorService)
                                                  .workerExecutorService(executorService)
                                                  .initialSegmentCount(8)
                                                  .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();

        executorService.shutdown();
    }

    @Test
    void testStartingProcessorClaimsAllAvailableTokens() {
        List<EventMessage<Integer>> events = IntStream.range(0, 100).mapToObj(GenericEventMessage::new).collect(Collectors.toList());
        when(stubEventHandler.canHandle(any(), any())).thenAnswer(i -> i.getArgument(0, EventMessage.class).getPayload().equals(i.getArgument(1, Segment.class).getSegmentId()));
        when(stubEventHandler.canHandleType(any())).thenReturn(true);

        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertEquals(8, testSubject.processingStatus().size());
        });


        assertWithin(2, TimeUnit.SECONDS, () -> {
            long nonNullTokens = IntStream.range(0, 8).mapToObj(i -> tokenStore.fetchToken(testSubject.getName(), i))
                                          .filter(Objects::nonNull)
                                          .count();
            assertEquals(8, nonNullTokens);
        });
        assertEquals(8, testSubject.processingStatus().size());
    }

    @Test
    void testAllTokensUpdatedToLatestValue() {
        List<EventMessage<Integer>> events = IntStream.range(0, 100).mapToObj(GenericEventMessage::new).collect(Collectors.toList());
        when(stubEventHandler.canHandle(any(), any())).thenAnswer(i -> i.getArgument(0, EventMessage.class).getPayload().equals(i.getArgument(1, Segment.class).getSegmentId()));
        when(stubEventHandler.canHandleType(any())).thenReturn(true);

        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertEquals(8, testSubject.processingStatus().size());
        });


        assertWithin(2, TimeUnit.SECONDS, () -> {
            long lowestToken = IntStream.range(0, 8).mapToObj(i -> tokenStore.fetchToken(testSubject.getName(), i))
                                        .mapToLong(this::tokenPosition)
                                        .min().orElse(-1);

            assertEquals(100, lowestToken);
        });
    }

    private long tokenPosition(TrackingToken token) {
        return token == null ? 0 : token.position().orElseThrow(IllegalArgumentException::new);
    }

    @Test
    void testExceptionWhileHandlingEventAbortsWorker() throws Exception {
        List<EventMessage<Integer>> events = Stream.of(1, 2, 2, 4, 5).map(GenericEventMessage::new).collect(Collectors.toList());
        when(stubEventHandler.canHandle(any(), any())).thenAnswer(i -> i.getArgument(0, EventMessage.class).getPayload().equals(i.getArgument(1, Segment.class).getSegmentId()));
        when(stubEventHandler.canHandleType(any())).thenReturn(true);
        doThrow(new RuntimeException("Simulating worker failure")).doNothing().when(stubEventHandler).handle(argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())), any());

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertEquals(8, testSubject.processingStatus().size());
        });
        assertEquals(8, tokenStore.fetchSegments("test").length);

        verify(stubEventHandler, never()).canHandle(any(), any());

        events.forEach(e -> stubMessageSource.publishMessage(e));

        assertWithin(1, TimeUnit.SECONDS, () -> {
            try {
                verify(stubEventHandler).handle(argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())), argThat(s -> s.getSegmentId() == events.get(2).getPayload()));
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
        Stream.of(1, 2, 2, 4, 5).map(GenericEventMessage::new).forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> {
            testSubject.processingStatus().values().forEach(status ->
                                                                    assertEquals(5, status.getCurrentPosition().orElse(0))
            );
        });
    }

    @Test
    void testShutdownCompletesAfterAbortingWorkPackages() throws InterruptedException, ExecutionException, TimeoutException {
        testSubject.start();
        Stream.of(1, 2, 2, 4, 5).map(GenericEventMessage::new).forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        testSubject.shutdownAsync().get(1, TimeUnit.SECONDS);
        assertEquals(0, testSubject.processingStatus().size());

        assertFalse(executorService.isShutdown());
    }

    @Test
    void stoppingUnstartedProcessorReturnsCompletedFuture() {
        assertTrue(testSubject.shutdownAsync().isDone());
    }

    @Test
    void stoppingProcessorTwiceReturnsSameFuture() {
        testSubject.start();

        CompletableFuture<Void> future1 = testSubject.shutdownAsync();
        CompletableFuture<Void> future2 = testSubject.shutdownAsync();

        assertSame(future1, future2);

    }

    @Test
    void testStartFailsWhenShutdownIsInProgress() throws Exception {
        // use a CountDownLatch to Block worker threads from actually doing work (and shutting down successfully)
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(i -> {
            cdl.await();
            return i.callRealMethod();
        }).when(stubEventHandler).handle(any(), any());

        testSubject.start();
        Stream.of(1, 2, 2, 4, 5).map(GenericEventMessage::new).forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        CompletableFuture<Void> shutdownComplete = testSubject.shutdownAsync();
        assertThrows(IllegalStateException.class, () -> testSubject.start());
        // unblock the Worker threads
        cdl.countDown();
        shutdownComplete.get(1, TimeUnit.SECONDS);

        // this is allowed
        assertDoesNotThrow(() -> testSubject.start());

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
                public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
                    return peek().isPresent();
                }

                @Override
                public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
                    TrackedEventMessage<?> next = peek().orElseThrow(() -> new RuntimeException("The processor should never perform a blocking call"));
                    this.lastToken = (int) next.trackingToken().position().orElseThrow(() -> new UnsupportedOperationException("Not supported"));
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