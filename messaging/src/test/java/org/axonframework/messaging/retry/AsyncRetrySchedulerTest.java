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
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncRetrySchedulerTest {

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
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        MessageStream<Message<?>> actual = testSubject.scheduleRetry(GenericMessage.asMessage(
                                                                             "stub"),
                                                                     null,
                                                                     new MockException(
                                                                             "Simulating exception"),
                                                                     dispatcher);
        assertTrue(actual.asCompletableFuture().isCompletedExceptionally());
        verify(dispatcher, never()).dispatch(any(), any());
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldScheduleRetryIfPolicyOutcomeIsRetry() {
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenReturn(MessageStream.empty());
        Message<Object> message = GenericMessage.asMessage("stub");
        MessageStream<Message<?>> actual = testSubject.scheduleRetry(message,
                                                                     null,
                                                                     new MockException(
                                                                             "Simulating exception"),
                                                                     dispatcher);
        // make sure the policy as asked with the right details
        verify(retryPolicy).defineFor(eq(message), isA(MockException.class), argThat(List::isEmpty));

        assertFalse(actual.asCompletableFuture().isDone());
        ScheduledTask scheduledTask = scheduledTasks.poll();
        assertNotNull(scheduledTask, "Expected task to have been scheduled");
        assertEquals(1, scheduledTask.delay);
        assertEquals(TimeUnit.SECONDS, scheduledTask.unit);

        scheduledTask.task.run();

        verify(dispatcher).dispatch(eq(message), any());
        assertTrue(actual.asCompletableFuture().isDone());
        verifyNoMoreInteractions(retryPolicy);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRescheduleAgainWhenRetryReturnsFailedStream() {
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any()))
                .thenReturn(MessageStream.failed(new MockException("Repeated failure")))
                .thenReturn(MessageStream.empty());

        Message<Object> message = GenericMessage.asMessage("stub");
        MessageStream<Message<?>> actual = testSubject.scheduleRetry(message,
                                                                     null,
                                                                     new MockException(
                                                                             "Simulating exception"),
                                                                     dispatcher);

        verify(retryPolicy).defineFor(eq(message), isA(MockException.class), argThat(List::isEmpty));

        assertFalse(actual.asCompletableFuture().isDone());
        ScheduledTask scheduledTask = scheduledTasks.poll();
        assertNotNull(scheduledTask, "Expected task to have been scheduled");
        assertEquals(1, scheduledTask.delay);
        assertEquals(TimeUnit.SECONDS, scheduledTask.unit);

        scheduledTask.task.run();
        verify(retryPolicy).defineFor(eq(message), isA(MockException.class), argThat(h -> h.size() == 1));

        verify(dispatcher, times(1)).dispatch(eq(message), any());
        assertFalse(actual.asCompletableFuture().isDone());

        scheduledTasks.remove().task.run();
        assertTrue(actual.asCompletableFuture().isDone());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFailedStreamIfFailureIsNotFirstItemInStream() {
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenAnswer(i -> MessageStream.just(GenericMessage.asMessage("OK"))
                                                                             .concatWith(MessageStream.failed(new MockException(
                                                                                     "Streaming error"))));
        MessageStream<Message<?>> actual = testSubject.scheduleRetry(GenericMessage.asMessage(
                                                                             "stub"),
                                                                     null,
                                                                     new MockException(
                                                                             "Simulating exception"),
                                                                     dispatcher);
        assertFalse(actual.asCompletableFuture().isDone());
        verify(dispatcher, never()).dispatch(any(), any());
        scheduledTasks.remove().task.run();

        assertTrue(actual.asCompletableFuture().isDone());

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
        policyOutcome.set(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
        RetryScheduler.Dispatcher<Message<Object>, Message<?>> dispatcher = mock();
        when(dispatcher.dispatch(any(), any())).thenAnswer(i -> MessageStream.failed(new MockException("Retry error")));
        MessageStream<Message<?>> actual = testSubject.scheduleRetry(GenericMessage.asMessage("stub"),
                                                                     null,
                                                                     new MockException("Simulating exception"),
                                                                     dispatcher);
        assertFalse(actual.asCompletableFuture().isDone());
        verify(dispatcher, never()).dispatch(any(), any());

        policyOutcome.set(RetryPolicy.Outcome.doNotReschedule());
        scheduledTasks.remove().task.run();

        assertTrue(actual.asCompletableFuture().isDone());
        assertInstanceOf(MockException.class, actual.asCompletableFuture().exceptionNow());
        assertEquals("Retry error", actual.asCompletableFuture().exceptionNow().getMessage());

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

    private static class TestRetryPolicy implements RetryPolicy {

        private final AtomicReference<Outcome> policyOutcome;

        public TestRetryPolicy(AtomicReference<Outcome> policyOutcome) {
            this.policyOutcome = policyOutcome;
        }

        @Override
        public Outcome defineFor(@Nonnull Message<?> message, @Nonnull Throwable cause,
                                 @Nonnull List<Class<? extends Throwable>[]> previousFailures) {
            return policyOutcome.get();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {

        }
    }

    private static class ScheduledTask {

        private final Runnable task;
        private final long delay;
        private final TimeUnit unit;

        public ScheduledTask(Runnable task, long delay, TimeUnit unit) {
            this.task = task;
            this.delay = delay;
            this.unit = unit;
        }
    }
}