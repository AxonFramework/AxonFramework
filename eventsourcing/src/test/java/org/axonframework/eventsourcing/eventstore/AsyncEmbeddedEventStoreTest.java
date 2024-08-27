package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.FutureUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Test suite validating the {@link AsyncEmbeddedEventStore} for different implementations of the
 * {@link AsyncEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
abstract class AsyncEmbeddedEventStoreTest<ESE extends AsyncEventStorageEngine> {

    private ESE storageEngine;
    private Instant fixedTime;
    private Clock clock;

    private AsyncEmbeddedEventStore testSubject;

    @BeforeEach
    void setUp() {
        storageEngine = spy(buildStorageEngine());
        fixedTime = Instant.now();
        clock = Clock.fixed(fixedTime, ZoneId.systemDefault());


        testSubject = new AsyncEmbeddedEventStore(storageEngine, clock);
    }

    /**
     * Constructs the {@link AsyncEventStorageEngine} used in this test suite.
     *
     * @return The {@link AsyncEventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine();

    @Test
    void currentTransactionConstructsNewAppendEventTransactionWhichAppendsEventsAsExpected() {
        EventMessage<?> testEvent = eventMessage(0);
        AppendCondition testCondition = AppendCondition.none();
        AtomicBoolean onAppendInvoked = new AtomicBoolean(false);

        // TODO or use the StubProcessingContext, which still needs an implementation of the phase handlers?
        AsyncUnitOfWork uow = new AsyncUnitOfWork();

        uow.whenComplete(context -> verify(storageEngine).appendEvents(testCondition, testEvent));

        CompletableFuture<EventStoreTransaction> result = uow.executeWithResult(
                testContext -> {
                    EventStoreTransaction currentTransaction = testSubject.transaction(testContext, "any-context");
                    testContext.runOnPostInvocation(context -> {
                        currentTransaction.onAppend(event -> onAppendInvoked.set(true));
                        currentTransaction.appendEvent(testEvent);
                    });
                    return CompletableFuture.completedFuture(currentTransaction);
                }
        );

        assertFalse(result.isCompletedExceptionally(), () -> FutureUtils.unwrapMessage(result.exceptionNow()));
        EventStoreTransaction resultTransaction = result.join();

        resultTransaction.onAppend(event -> onAppendInvoked.set(true));
        resultTransaction.appendEvent(testEvent);

        assertTrue(onAppendInvoked.get());
    }

    // TODO Sourcing test
    // TODO Streaming test

    @Test
    void tailTokenReturnsTokenBasedOnFirstAppendedEvent() {
        CompletableFuture<TrackingToken> result = testSubject.tailToken();

        assertTrue(result.isDone());

        verify(storageEngine).tailToken();
    }

    @Test
    void headTokenReturnsTokenBasedOnLastAppendedEvent() {
        CompletableFuture<TrackingToken> result = testSubject.headToken();

        assertTrue(result.isDone());

        verify(storageEngine).headToken();
    }

    @Test
    void tokenAtReturnsHeadTokenWhenThereAreNoEventsBeforeTheGivenAt() {
        Instant testAt = clock.instant();

        CompletableFuture<TrackingToken> result = testSubject.tokenAt(testAt);

        assertTrue(result.isDone());

        verify(storageEngine).tokenAt(testAt);
    }

    @Test
    void tokenAtRetrievesTokenFromStorageEngine() {
        // TODO
    }

    @Test
    void tokenSinceReturnsHeadTokenWhenThereAreNoEventsBeforeTheGivenSince() {
        Duration testSince = Duration.ofSeconds(50);
        Instant expectedAt = fixedTime.minus(testSince);

        CompletableFuture<TrackingToken> result = testSubject.tokenSince(testSince);

        assertTrue(result.isDone());

        verify(storageEngine).tokenAt(expectedAt);
    }

    @Test
    void tokenSinceRetrievesTokenFromStorageEngineThroughTokenAt() {
        // TODO
    }

    private static EventMessage<?> eventMessage(int seq) {
        return GenericEventMessage.asEventMessage("Event[" + seq + "]");
    }
}