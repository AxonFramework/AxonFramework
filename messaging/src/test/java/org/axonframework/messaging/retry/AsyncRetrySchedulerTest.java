/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.retry;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncRetrySchedulerTest {

    private static final QualifiedName TEST_NAME = new QualifiedName("test", "message", "0.0.1");

    private AsyncRetryScheduler testSubject;
    private RetryPolicy retryPolicy;
    private ScheduledExecutorService executor;
    private AtomicReference<RetryPolicy.Outcome> policyOutcome;
    private Queue<ScheduledTask> scheduledTasks;

    @BeforeEach
    void setup() {
        policyOutcome = new AtomicReference<>(RetryPolicy.Outcome.doNotReschedule());
        scheduledTasks = new LinkedList<>();
        retryPolicy = spy(new TestRetryPolicy(policyOutcome));
        executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(
                i -> {
                    scheduledTasks.add(new ScheduledTask(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
                    return null;
                }
        );
        testSubject = new AsyncRetryScheduler(retryPolicy, executor);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFailedStreamIfPolicyOutcomeIsNoRetry() {
        Message<Object> testMessage = new GenericMessage<>(TEST_NAME, "stub");
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();

        MessageStream<Message<?>> actual =
                testSubject.scheduleRetry(testMessage, null, new MockException("Simulating exception"), dispatcher);

        assertTrue(actual.firstAsCompletableFuture().isCompletedExceptionally());
        verify(dispatcher, never()).dispatch(any(), any());
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldScheduleRetryIfPolicyOutcomeIsRetry() {
        Message<Object> testMessage = new GenericMessage<>(TEST_NAME, "stub");
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenReturn(MessageStream.empty());

        MessageStream<Message<?>> actual =
                testSubject.scheduleRetry(testMessage, null, new MockException("Simulating exception"), dispatcher);
        // make sure the policy as asked with the right details
        verify(retryPolicy).defineFor(eq(testMessage), isA(MockException.class), argThat(List::isEmpty));

        assertFalse(actual.firstAsCompletableFuture().isDone());
        ScheduledTask scheduledTask = scheduledTasks.poll();
        assertNotNull(scheduledTask, "Expected task to have been scheduled");
        assertEquals(1, scheduledTask.delay);
        assertEquals(TimeUnit.SECONDS, scheduledTask.unit);

        scheduledTask.task.run();

        verify(dispatcher).dispatch(eq(testMessage), any());
        assertTrue(actual.firstAsCompletableFuture().isDone());
        verifyNoMoreInteractions(retryPolicy);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRescheduleAgainWhenRetryReturnsFailedStream() {
        Message<Object> testMessage = new GenericMessage<>(TEST_NAME, "stub");
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any()))
                .thenReturn(MessageStream.failed(new MockException("Repeated failure")))
                .thenReturn(MessageStream.empty());

        MessageStream<Message<?>> actual =
                testSubject.scheduleRetry(testMessage, null, new MockException("Simulating exception"), dispatcher);

        verify(retryPolicy).defineFor(eq(testMessage), isA(MockException.class), argThat(List::isEmpty));

        assertFalse(actual.firstAsCompletableFuture().isDone());
        ScheduledTask scheduledTask = scheduledTasks.poll();
        assertNotNull(scheduledTask, "Expected task to have been scheduled");
        assertEquals(1, scheduledTask.delay);
        assertEquals(TimeUnit.SECONDS, scheduledTask.unit);

        scheduledTask.task.run();
        verify(retryPolicy).defineFor(eq(testMessage), isA(MockException.class), argThat(h -> h.size() == 1));

        verify(dispatcher, times(1)).dispatch(eq(testMessage), any());
        assertFalse(actual.firstAsCompletableFuture().isDone());

        scheduledTasks.remove().task.run();
        assertTrue(actual.firstAsCompletableFuture().isDone());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFailedStreamIfFailureIsNotFirstItemInStream() {
        Message<Object> testMessage = new GenericMessage<>(TEST_NAME, "stub");
        Message<String> responseMessage = new GenericMessage<>(TEST_NAME, "OK");
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenAnswer(
                i -> MessageStream.just(responseMessage)
                                  .concatWith(MessageStream.failed(new MockException("Streaming error")))
        );

        MessageStream<Message<?>> actual =
                testSubject.scheduleRetry(testMessage, null, new MockException("Simulating exception"), dispatcher);

        assertFalse(actual.firstAsCompletableFuture().isDone());
        verify(dispatcher, never()).dispatch(any(), any());
        scheduledTasks.remove().task.run();

        assertTrue(actual.firstAsCompletableFuture().isDone());

        StepVerifier.create(actual.asFlux())
                    .expectNextCount(1)
                    .verifyError(MockException.class);

        // no tasks have been scheduled for this failure
        assertTrue(scheduledTasks.isEmpty());
        verify(dispatcher, times(1)).dispatch(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotScheduleAnotherRetryWhenPolicyIndicatesSo() {
        Message<Object> testMessage = new GenericMessage<>(TEST_NAME, "stub");
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenAnswer(i -> MessageStream.failed(new MockException("Retry error")));

        MessageStream<Message<?>> actual =
                testSubject.scheduleRetry(testMessage, null, new MockException("Simulating exception"), dispatcher);

        assertFalse(actual.firstAsCompletableFuture().isDone());
        verify(dispatcher, never()).dispatch(any(), any());

        policyOutcome.set(RetryPolicy.Outcome.doNotReschedule());
        scheduledTasks.remove().task.run();

        assertTrue(actual.firstAsCompletableFuture().isDone());
        assertInstanceOf(MockException.class, actual.firstAsCompletableFuture().exceptionNow());
        assertEquals("Retry error", actual.firstAsCompletableFuture().exceptionNow().getMessage());

        // no tasks have been scheduled for this failure
        assertTrue(scheduledTasks.isEmpty());
        verify(dispatcher, times(1)).dispatch(any(), any());
    }

    @Test
    void shouldDescribeItsProperties() {
        ComponentDescriptor mock = mock();
        testSubject.describeTo(mock);
        verify(mock).describeProperty("retryPolicy", retryPolicy);
        verify(mock).describeProperty("executor", executor);
    }

    @SuppressWarnings("ClassCanBeRecord") // This class is spied, so it cannot be final like a record
    private static class TestRetryPolicy implements RetryPolicy {

        private final AtomicReference<Outcome> policyOutcome;

        private TestRetryPolicy(AtomicReference<Outcome> policyOutcome) {
            this.policyOutcome = policyOutcome;
        }

        @Override
        public Outcome defineFor(@Nonnull Message<?> message,
                                 @Nonnull Throwable cause,
                                 @Nonnull List<Class<? extends Throwable>[]> previousFailures) {
            return policyOutcome.get();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {

        }

        public AtomicReference<Outcome> policyOutcome() {
            return policyOutcome;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (TestRetryPolicy) obj;
            return Objects.equals(this.policyOutcome, that.policyOutcome);
        }

        @Override
        public int hashCode() {
            return Objects.hash(policyOutcome);
        }

        @Override
        public String toString() {
            return "TestRetryPolicy[" +
                    "policyOutcome=" + policyOutcome + ']';
        }
    }

    private record ScheduledTask(Runnable task, long delay, TimeUnit unit) {

    }
}