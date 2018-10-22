/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.deadline;

import org.axonframework.common.Registration;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.test.FixtureExecutionException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;

/**
 * Stub implementation of {@link DeadlineManager}. Records all scheduled and met deadlines.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class StubDeadlineManager implements DeadlineManager {

    private final NavigableSet<ScheduledDeadlineInfo> schedules = new TreeSet<>();
    private final List<ScheduledDeadlineInfo> deadlinesMet = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private Instant currentDateTime;

    private final List<MessageDispatchInterceptor<? super DeadlineMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageHandlerInterceptor<? super DeadlineMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();

    /**
     * Initializes the manager with {@link ZonedDateTime#now()} as current time.
     */
    public StubDeadlineManager() {
        this(ZonedDateTime.now());
    }

    /**
     * Initializes the manager with provided {@code currentDateTime} as current time.
     *
     * @param currentDateTime The instance to use as current date and time
     */
    public StubDeadlineManager(TemporalAccessor currentDateTime) {
        this.currentDateTime = Instant.from(currentDateTime);
    }

    /**
     * Resets the initial "current time" of this manager. Must be called before any deadlines are scheduled.
     *
     * @param currentDateTime The instant to use as the current date and time
     * @throws IllegalStateException when calling this method after deadlines are scheduled
     */
    public void initializeAt(TemporalAccessor currentDateTime) throws IllegalStateException {
        if (!schedules.isEmpty()) {
            throw new IllegalStateException("Initializing the deadline manager at a specific dateTime must take place "
                                                    + "before any deadlines are scheduled");
        }
        this.currentDateTime = Instant.from(currentDateTime);
    }

    @Override
    public String schedule(Instant triggerDateTime,
                         String deadlineName,
                         Object payloadOrMessage,
                         ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, payloadOrMessage);
        deadlineMessage = processDispatchInterceptors(deadlineMessage);
        schedules.add(new ScheduledDeadlineInfo(triggerDateTime,
                                                deadlineName,
                                                deadlineMessage.getIdentifier(),
                                                counter.getAndIncrement(),
                                                deadlineMessage,
                                                deadlineScope));
        return deadlineMessage.getIdentifier();
    }

    @Override
    public String schedule(Duration triggerDuration,
                           String deadlineName,
                           Object payloadOrMessage,
                           ScopeDescriptor deadlineScope) {
        return schedule(currentDateTime.plus(triggerDuration), deadlineName, payloadOrMessage, deadlineScope);
    }

    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        schedules.removeIf(scheduledDeadline -> scheduledDeadline.getDeadlineName().equals(deadlineName)
                && scheduledDeadline.getScheduleId().equals(scheduleId));
    }

    @Override
    public void cancelAll(String deadlineName) {
        schedules.removeIf(scheduledDeadline -> scheduledDeadline.getDeadlineName().equals(deadlineName));
    }

    /**
     * @return scheduled deadlines (which have not been met)
     */
    public List<ScheduledDeadlineInfo> getScheduledDeadlines() {
        return new ArrayList<>(schedules);
    }

    /**
     * @return deadlines which have been met
     */
    public List<ScheduledDeadlineInfo> getDeadlinesMet() {
        return Collections.unmodifiableList(deadlinesMet);
    }

    /**
     * @return the current date and time used by the manager
     */
    public Instant getCurrentDateTime() {
        return currentDateTime;
    }

    /**
     * Advances the "current time" of the manager to the next scheduled deadline, and returns that deadline. In theory,
     * this may cause "current time" to move backwards.
     *
     * @return {@link ScheduledDeadlineInfo} of the first scheduled deadline
     */
    public ScheduledDeadlineInfo advanceToNextTrigger() {
        ScheduledDeadlineInfo nextItem = schedules.pollFirst();
        if (nextItem == null) {
            throw new NoSuchElementException("There are no scheduled deadlines");
        }
        if (nextItem.getScheduleTime().isAfter(currentDateTime)) {
            currentDateTime = nextItem.getScheduleTime();
        }
        deadlinesMet.add(nextItem);
        return nextItem;
    }

    /**
     * Advances time to the given {@code newDateTime} and invokes the given {@code deadlineConsumer} for each deadline
     * scheduled until that time.
     *
     * @param newDateTime      The time to advance the "current time" of the manager to
     * @param deadlineConsumer The consumer to invoke for each deadline to trigger
     */
    public void advanceTimeTo(Instant newDateTime, DeadlineConsumer deadlineConsumer) {
        while (!schedules.isEmpty() && !schedules.first().getScheduleTime().isAfter(newDateTime)) {
            ScheduledDeadlineInfo scheduledDeadlineInfo = advanceToNextTrigger();
            DeadlineMessage<?> consumedMessage = consumeDeadline(deadlineConsumer, scheduledDeadlineInfo);
            deadlinesMet.remove(scheduledDeadlineInfo);
            deadlinesMet.add(scheduledDeadlineInfo.recreateWithNewMessage(consumedMessage));
        }
        if (newDateTime.isAfter(currentDateTime)) {
            currentDateTime = newDateTime;
        }
    }

    /**
     * Advances time by the given {@code duration} and invokes the given {@code deadlineConsumer} for each deadline
     * scheduled until that time.
     *
     * @param duration         The amount of time to advance the "current time" of the manager with
     * @param deadlineConsumer The consumer to invoke for each deadline to trigger
     */
    public void advanceTimeBy(Duration duration, DeadlineConsumer deadlineConsumer) {
        advanceTimeTo(currentDateTime.plus(duration), deadlineConsumer);
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super DeadlineMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super DeadlineMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        return () -> handlerInterceptors.remove(handlerInterceptor);
    }

    @SuppressWarnings("unchecked")
    private <T> DeadlineMessage<T> processDispatchInterceptors(DeadlineMessage<T> message) {
        DeadlineMessage<T> intercepted = message;
        for (MessageDispatchInterceptor<? super DeadlineMessage<?>> interceptor : dispatchInterceptors) {
            intercepted = (DeadlineMessage<T>) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    private DeadlineMessage<?> consumeDeadline(DeadlineConsumer deadlineConsumer,
                                               ScheduledDeadlineInfo scheduledDeadlineInfo) {
        DefaultUnitOfWork<? extends DeadlineMessage<?>> uow =
                DefaultUnitOfWork.startAndGet(scheduledDeadlineInfo.deadlineMessage());
        InterceptorChain chain = new DefaultInterceptorChain<>(uow, handlerInterceptors, deadlineMessage -> {
            deadlineConsumer.consume(scheduledDeadlineInfo.getDeadlineScope(), deadlineMessage);
            return deadlineMessage;
        });
        ResultMessage<?> resultMessage = uow.executeWithResult(chain::proceed);
        if (resultMessage.isExceptional()) {
            Throwable e = resultMessage.exceptionResult();
            throw new FixtureExecutionException("Exception occurred while handling the deadline", e);
        }
        return (DeadlineMessage<?>) resultMessage.getPayload();
    }
}
