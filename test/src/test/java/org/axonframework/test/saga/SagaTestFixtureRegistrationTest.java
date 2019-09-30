package org.axonframework.test.saga;

import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.*;

import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

/**
 * Test class dedicated to validating custom registered components on a {@link SagaTestFixture} instance.
 */
public class SagaTestFixtureRegistrationTest {

    private SagaTestFixture<SomeTestSaga> fixture;
    private AtomicInteger startRecordingCount;

    @Before
    public void setUp() {
        fixture = new SagaTestFixture<>(SomeTestSaga.class);
        startRecordingCount = new AtomicInteger();
        fixture.registerStartRecordingCallback(startRecordingCount::getAndIncrement);
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenPublishingAnEvent() {
        fixture.givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenPublishingA(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenTimeAdvances() {
        fixture.givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenTimeAdvancesTo(now());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenTimeElapses() {
        fixture.givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenTimeElapses(ofSeconds(5));
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    public void testCustomListenerInvocationErrorHandlerIsUsed() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);

        ListenerInvocationErrorHandler testSubject = (exception, event, eventHandler) ->
                assertEquals(testEvent.getException().getMessage(), exception.getMessage());

        fixture.registerListenerInvocationErrorHandler(testSubject);
        // This will trigger the test subject due to how the event is configured
        fixture.givenAPublished(testEvent);
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

            public Boolean shouldThrowException() {
                return shouldThrowException;
            }

            public Exception getException() {
                return exception;
            }
        }
    }
}
