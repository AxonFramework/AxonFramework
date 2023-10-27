/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.Scope;
import org.axonframework.messaging.ScopeDescriptor;

import java.util.concurrent.Callable;

/**
 * Abstract base class of a component that models an aggregate's life cycle.
 */
public abstract class AggregateLifecycle extends Scope {

    /**
     * Apply a {@link org.axonframework.eventhandling.DomainEventMessage} with given payload and metadata (metadata
     * from interceptors will be combined with the provided metadata). Applying events means they are immediately
     * applied (published) to the aggregate and scheduled for publication to other event handlers.
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
     * Apply a {@link org.axonframework.eventhandling.DomainEventMessage} with given payload without metadata (though
     * interceptors can also be used to provide metadata). Applying events means they are immediately applied
     * (published) to the aggregate and scheduled for publication to other event handlers.
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
     * Creates a new aggregate instance. In order for new aggregate to be created, a {@link Repository} should be
     * available to the current aggregate. {@link Repository} of an aggregate to be created is exposed to the current
     * aggregate via {@link RepositoryProvider}.
     *
     * @param <T>           type of new aggregate to be created
     * @param aggregateType type of new aggregate to be created
     * @param factoryMethod factory method which creates new aggregate
     * @return a new aggregate instance
     * @throws Exception thrown if something goes wrong during instantiation of new aggregate
     */
    public static <T> Aggregate<T> createNew(Class<T> aggregateType, Callable<T> factoryMethod)
            throws Exception {
        if (!isLive()) {
            throw new UnsupportedOperationException(
                    "Aggregate is still initializing its state, creation of new aggregates is not possible");
        }
        return getInstance().doCreateNew(aggregateType, factoryMethod);
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
     * Gets the version of the aggregate.
     *
     * @return the current version of the aggregate
     */
    public static Long getVersion() {
        return getInstance().version();
    }

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
     * Get the current {@link AggregateLifecycle} instance for the current thread. If none exists an {@link
     * IllegalStateException} is thrown.
     *
     * @return the thread's current {@link AggregateLifecycle}
     */
    protected static AggregateLifecycle getInstance() {
        return Scope.getCurrentScope();
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
     * Gets the version of the aggregate.
     *
     * @return the current version of the aggregate
     */
    protected abstract Long version();

    /**
     * Marks this aggregate as deleted. Implementations may react differently to aggregates marked for deletion.
     * Typically, Event Sourced Repositories will ignore the marking and expect deletion to be provided as part of Event
     * information.
     */
    protected abstract void doMarkDeleted();

    /**
     * Apply a {@link org.axonframework.eventhandling.DomainEventMessage} with given payload and metadata (metadata from
     * interceptors will be combined with the provided metadata). The event should be applied to the aggregate
     * immediately and scheduled for publication to other event handlers.
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
     * Creates a new aggregate instance. In order for new aggregate to be created, a {@link Repository} should be
     * available to the current aggregate. {@link Repository} of an aggregate to be created is exposed to the current
     * aggregate via {@link RepositoryProvider}.
     *
     * @param <T>           type of new aggregate to be created
     * @param aggregateType type of new aggregate to be created
     * @param factoryMethod factory method which creates new aggregate
     * @return a new aggregate instance
     * @throws Exception thrown if something goes wrong during instantiation of new aggregate
     */
    protected abstract <T> Aggregate<T> doCreateNew(Class<T> aggregateType, Callable<T> factoryMethod) throws Exception;

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

    @Override
    public ScopeDescriptor describeScope() {
        return new AggregateScopeDescriptor(type(), this::identifier);
    }

    /**
     * Retrieve a {@link String} denoting the type of this Aggregate.
     *
     * @return a {@link String} denoting the type of this Aggregate
     */
    protected abstract String type();

    /**
     * Retrieve a {@link Object} denoting the identifier of this Aggregate.
     *
     * @return a {@link Object} denoting the identifier of this Aggregate
     */
    protected abstract Object identifier();
}
