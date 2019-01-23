package org.axonframework.test.saga;

import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.Before;
import org.junit.Test;
import org.axonframework.test.saga.SagaTestFixtureTest.MyTestSaga.MyEvent;
import org.junit.*;

import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class SagaTestFixtureTest {

    private SagaTestFixture<MyTestSaga> fixture;
    private AtomicInteger startRecordingCount;

    @Before
    public void before() {
        fixture = new SagaTestFixture<>(MyTestSaga.class);
        startRecordingCount = new AtomicInteger();
        fixture.registerStartRecordingCallback(startRecordingCount::getAndIncrement);
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenPublishingAnEvent() {
        fixture.givenAPublished(new MyTestSaga.MyEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenPublishingA(new MyTestSaga.MyEvent());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenTimeAdvances() throws Exception {
        fixture.givenAPublished(new MyTestSaga.MyEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenTimeAdvancesTo(now());
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    @Test
    public void startRecordingCallbackIsInvokedOnWhenTimeElapses() throws Exception {
        fixture.givenAPublished(new MyTestSaga.MyEvent());
        assertThat(startRecordingCount.get(), equalTo(0));

        fixture.whenTimeElapses(ofSeconds(5));
        assertThat(startRecordingCount.get(), equalTo(1));
    }

    public static class MyTestSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handle(MyEvent e) {
            // don't care
        }

        public static class MyEvent {

            public String getId() {
                return "42";
            }
        }
    }
}
