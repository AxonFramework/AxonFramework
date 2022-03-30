/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.async;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessingStrategy;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of a {@link EventProcessingStrategy} that creates event processing tasks for asynchronous execution.
 * Clients can decide if events may be processed in sequence or in parallel using a {@link SequencingPolicy}.
 *
 * @author Rene de Waele
 */
public class AsynchronousEventProcessingStrategy implements EventProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousEventProcessingStrategy.class);
    private final String scheduledEventsKey = this + "_SCHEDULED_EVENTS";
    private final Executor executor;
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private final ConcurrentMap<Object, EventProcessorTask> currentTasks = new ConcurrentHashMap<>();

    /**
     * Initializes a new {@link AsynchronousEventProcessingStrategy} that uses the given {@code executor} to execute
     * event processing tasks and {@code sequencingPolicy} that determines if an event may be processed in sequence or
     * in parallel.
     *
     * @param executor         the event processing job executor
     * @param sequencingPolicy the policy that determines if an event may be processed in sequence or in parallel
     */
    public AsynchronousEventProcessingStrategy(Executor executor,
                                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this.executor = requireNonNull(executor);
        this.sequencingPolicy = requireNonNull(sequencingPolicy);
    }

    @Override
    public void handle(@Nonnull List<? extends EventMessage<?>> events,
                       @Nonnull Consumer<List<? extends EventMessage<?>>> processor) {
        if (CurrentUnitOfWork.isStarted()) {
            UnitOfWork<?> unitOfWorkRoot = CurrentUnitOfWork.get().root();
            unitOfWorkRoot.getOrComputeResource(scheduledEventsKey, key -> {
                List<EventMessage<?>> allEvents = new ArrayList<>();
                unitOfWorkRoot.afterCommit(uow -> schedule(allEvents, processor));
                return allEvents;
            }).addAll(events);
        } else {
            schedule(events, processor);
        }
    }

    /**
     * Schedules this task for execution when all pre-conditions have been met.
     *
     * @param events    The messages to schedule for processing
     * @param processor The component that will perform the actual processing
     */
    protected void schedule(List<? extends EventMessage<?>> events,
                            Consumer<List<? extends EventMessage<?>>> processor) {
        Map<Object, List<EventMessage<?>>> groupedEvents = new HashMap<>();
        for (EventMessage<?> event : events) {
            groupedEvents.computeIfAbsent(sequencingPolicy.getSequenceIdentifierFor(event), key -> new ArrayList<>())
                    .add(event);
        }
        groupedEvents.forEach((sequenceIdentifier, eventGroup) -> {
            if (sequenceIdentifier == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling Event for full concurrent processing {}",
                                 events.getClass().getSimpleName());
                }
                newProcessingScheduler(NoActionCallback.INSTANCE).scheduleEvents(eventGroup, processor);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling task of type [{}] for sequential processing in group [{}]",
                                 events.getClass().getSimpleName(), sequenceIdentifier.toString());
                }
                assignEventsToScheduler(eventGroup, sequenceIdentifier, processor);
            }
        });
    }

    private void assignEventsToScheduler(List<? extends EventMessage<?>> events, Object sequenceIdentifier,
                                         Consumer<List<? extends EventMessage<?>>> processor) {
        boolean taskScheduled = false;
        while (!taskScheduled) {
            EventProcessorTask currentScheduler = currentTasks.get(sequenceIdentifier);
            if (currentScheduler == null) {
                currentTasks.putIfAbsent(sequenceIdentifier,
                                         newProcessingScheduler(new SchedulerCleanUp(sequenceIdentifier)));
            } else {
                taskScheduled = currentScheduler.scheduleEvents(events, processor);
                if (!taskScheduled) {
                    // we know it can be cleaned up.
                    currentTasks.remove(sequenceIdentifier, currentScheduler);
                }
            }
        }
    }

    /**
     * Creates a new scheduler instance that schedules tasks on the executor service for the managed EventListener.
     *
     * @param shutDownCallback The callback that needs to be notified when the scheduler stops processing.
     * @return a new scheduler instance
     */
    protected EventProcessorTask newProcessingScheduler(EventProcessorTask.ShutdownCallback shutDownCallback) {
        if (logger.isDebugEnabled()) {
            logger.debug("Initializing new processing scheduler.");
        }
        return new EventProcessorTask(executor, shutDownCallback);
    }

    private enum NoActionCallback implements EventProcessorTask.ShutdownCallback {
        INSTANCE;

        @Override
        public void afterShutdown(EventProcessorTask scheduler) {
        }
    }

    private final class SchedulerCleanUp implements EventProcessorTask.ShutdownCallback {

        private final Object sequenceIdentifier;

        private SchedulerCleanUp(Object sequenceIdentifier) {
            this.sequenceIdentifier = sequenceIdentifier;
        }

        @Override
        public void afterShutdown(EventProcessorTask scheduler) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cleaning up processing scheduler for sequence [{}]", sequenceIdentifier.toString());
            }
            currentTasks.remove(sequenceIdentifier, scheduler);
        }
    }
}
