/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Abstract base class of a component that models an aggregate's life cycle.
 */
public abstract class AggregateLifecycle {

    private static final ThreadLocal<AggregateLifecycle> CURRENT = new ThreadLocal<>();

    /**
     * Apply a {@link DomainEventMessage} with given payload and metadata (metadata from interceptors will be combined
     * with the provided metadata). Applying events means they are immediately applied (published) to the aggregate and
     * scheduled for publication to other event handlers.
     * <p/>
     * The event is applied on all entities part of this aggregate. If the event is applied from an event handler of the
     * aggregate and additional events need to be applied that depends on state changes brought about by the first event
     * use the returned {@link ApplyMore} instance.
     *
     * @param payload  the payload of the event to apply
     * @param metaData any meta-data that must be registered with the Event
     * @return a gizmo to apply additional events after the given event has been processed by the entire aggregate
     * @see ApplyMore
     */
    public static ApplyMore apply(Object payload, MetaData metaData) {
        return AggregateLifecycle.getInstance().doApply(payload, metaData);
    }

    /**
     * Apply a {@link DomainEventMessage} with given payload without metadata (though interceptors can also be used to
     * provide metadata). Applying events means they are immediately applied (published) to the aggregate and scheduled
     * for publication to other event handlers.
     * <p/>
     * The event is applied on all entities part of this aggregate. If the event is applied from an event handler of the
     * aggregate and additional events need to be applied that depends on state changes brought about by the first event
     * use the returned {@link ApplyMore} instance.
     *
     * @param payload the payload of the event to apply
     * @return a gizmo to apply additional events after the given event has been processed by the entire aggregate
     * @see ApplyMore
     */
    public static ApplyMore apply(Object payload) {
        return AggregateLifecycle.getInstance().doApply(payload, MetaData.emptyInstance());
    }

    /**
     * Indicates whether this Aggregate instance is 'live'. Events applied to a 'live' Aggregate represent events that
     * are currently happening, as opposed to events representing historic decisions used to reconstruct the
     * Aggregate's state.
     *
     * @return {@code true} if the aggregate is 'live', {@code false} if the aggregate is initializing state based on
     * historic events
     */
    public static boolean isLive() {
        return AggregateLifecycle.getInstance().getIsLive();
    }

    /**
     * Indicates whether this Aggregate instance is 'live'. This means events currently applied represent events that
     * are currently happening, as opposed to events representing historic decisions.
     *
     * @return {@code true} if the aggregate is 'live', {@code false} if the aggregate is initializing state based on
     * historic events
     */
    protected abstract boolean getIsLive();

    /**
     * Marks this aggregate as deleted, instructing a repository to remove that aggregate at an appropriate time.
     * <p/>
     * Note that different repository implementations may react differently to aggregates marked for deletion.
     * Typically, Event Sourced Repositories will ignore the marking and expect deletion to be provided as part of Event
     * information.
     */
    public static void markDeleted() {
        getInstance().doMarkDeleted();
    }

    /**
     * Returns the {@link AggregateLifecycle} for the current aggregate. If none was defined this method will throw
     * an exception.
     *
     * @return the {@link AggregateLifecycle} for the current aggregate
     */
    protected static AggregateLifecycle getInstance() {
        AggregateLifecycle instance = CURRENT.get();
        if (instance == null && CurrentUnitOfWork.isStarted()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            Set<AggregateLifecycle> managedAggregates = unitOfWork.getResource("ManagedAggregates");
            if (managedAggregates != null && managedAggregates.size() == 1) {
                instance = managedAggregates.iterator().next();
            }
        }
        if (instance == null) {
            throw new IllegalStateException("Cannot retrieve current AggregateLifecycle; none is yet defined");
        }
        return instance;
    }

    /**
     * Marks this aggregate as deleted. Implementations may react differently to aggregates marked for deletion.
     * Typically, Event Sourced Repositories will ignore the marking and expect deletion to be provided as part of Event
     * information.
     */
    protected abstract void doMarkDeleted();

    /**
     * Registers this aggregate with the current unit of work if one is started.
     */
    protected void registerWithUnitOfWork() {
        CurrentUnitOfWork.ifStarted(u -> u.getOrComputeResource("ManagedAggregates", k -> new HashSet<>()).add(this));
    }

    /**
     * Apply a {@link DomainEventMessage} with given payload and metadata (metadata from interceptors will be combined
     * with the provided metadata). The event should be applied to the aggregate immediately and scheduled for
     * publication to other event handlers.
     * <p/>
     * The event should be applied on all entities part of this aggregate. If the event is applied from an event handler
     * of the aggregate and additional events need to be applied that depends on state changes brought about by the
     * first event the returned {@link ApplyMore} instance should allow for additional events to be applied after this
     * event.
     *
     * @param payload  the payload of the event to apply
     * @param metaData any meta-data that must be registered with the Event
     * @return a gizmo to apply additional events after the given event has been processed by the entire aggregate
     * @see ApplyMore
     */
    protected abstract <T> ApplyMore doApply(T payload, MetaData metaData);

    /**
     * Executes the given task and returns the result of the task. While the task is being executed the current
     * aggregate will be registered with the current thread as the 'current' aggregate.
     *
     * @param task the task to execute on the aggregate
     * @param <V>  the result of the task
     * @return the task's result
     * @throws Exception if executing the task causes an exception
     */
    protected <V> V executeWithResult(Callable<V> task) throws Exception {
        AggregateLifecycle existing = CURRENT.get();
        CURRENT.set(this);
        try {
            return task.call();
        } finally {
            if (existing == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(existing);
            }
        }
    }

    /**
     * Executes the given task. While the task is being executed the current aggregate will be registered with the
     * current thread as the 'current' aggregate.
     *
     * @param task the task to execute on the aggregate
     */
    protected void execute(Runnable task) {
        try {
            executeWithResult(() -> {
                task.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AggregateInvocationException("Exception while invoking a task for an aggregate", e);
        }
    }
}
