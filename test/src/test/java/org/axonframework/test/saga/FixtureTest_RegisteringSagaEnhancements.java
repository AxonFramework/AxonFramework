package org.axonframework.test.saga;

import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class dedicated to validating custom, saga specific, registered components on {@link SagaTestFixture}.
 *
 * @author Steven van Beelen
 */
class FixtureTest_RegisteringSagaEnhancements {

    private SagaTestFixture<SomeTestSaga> testSubject;

    private AtomicInteger startRecordingCount;

    @BeforeEach
    void setUp() {
        startRecordingCount = new AtomicInteger();

        testSubject = new SagaTestFixture<>(SomeTestSaga.class);
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenPublishingAnEvent() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        testSubject.whenPublishingA(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenTimeAdvances() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        testSubject.whenTimeAdvancesTo(now());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenTimeElapses() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        testSubject.whenTimeElapses(ofSeconds(5));
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    void customListenerInvocationErrorHandlerIsUsedInWhenPhase() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = (exception, event, eventHandler) ->
                assertEquals(testEvent.getException().getMessage(), exception.getMessage());
        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler);

        this.testSubject.givenNoPriorActivity()
                        .whenPublishingA(testEvent);
    }

    @Test
    void customListenerInvocationErrorHandlerIsUsedInGivenPhase() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = (exception, event, eventHandler) ->
                assertEquals(testEvent.getException().getMessage(), exception.getMessage());

        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler)
                        .proceedOnGivenPhaseExceptions(true);

        this.testSubject.givenAPublished(testEvent);
    }

    @Test
    void exceptionsAreRethrownAsFixtureExecutionExceptionDuringGivenPhaseWithoutInvokedCustomErrorHandler() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = spy(new LoggingErrorHandler());

        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler);

        assertThrows(FixtureExecutionException.class, () -> this.testSubject.givenAPublished(testEvent));
    }

    @Test
    void registeredResourceInjectorIsCalledUponFirstEventPublication() {
        AtomicBoolean assertion = new AtomicBoolean(false);
        testSubject.registerResourceInjector(saga -> assertion.set(true))
                   // Publishing a single event should trigger the creation and injection of resources
                   .givenAPublished(new SomeTestSaga.SomeEvent());

        assertTrue(assertion.get());
    }

    public static class SomeTestSaga {

        @SuppressWarnings("unused")
        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handle(SomeEvent event) throws Exception {
            if (event.shouldThrowException()) {
                throw event.getException();
            }
        }

        public static class SomeEvent {

            private final String id;
            private final Boolean shouldThrowException;
            private final Exception exception = new IllegalStateException("I was told to throw an exception");

            public SomeEvent() {
                this("42", false);
            }

            public SomeEvent(String id, Boolean shouldThrowException) {
                this.id = id;
                this.shouldThrowException = shouldThrowException;
            }

            public String getId() {
                return id;
            }

            Boolean shouldThrowException() {
                return shouldThrowException;
            }

            public Exception getException() {
                return exception;
            }
        }
    }
}
