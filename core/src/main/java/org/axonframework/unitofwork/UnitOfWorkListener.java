/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.EventMessage;

import java.util.List;
import java.util.Set;

/**
 * Interface describing a listener that is notified of state changes in the UnitOfWork it has been registered with.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface UnitOfWorkListener {

    /**
     * Invoked when the UnitOfWork is committed. The aggregate has been saved and the events have been scheduled for
     * dispatching. In some cases, the events could already have been dispatched. When processing of this method causes
     * an exception, a UnitOfWork may choose to call {@link #onRollback(Throwable)} consecutively.
     *
     * @see UnitOfWork#commit()
     */
    void afterCommit();

    /**
     * Invoked when the UnitOfWork is rolled back. The UnitOfWork may choose to invoke this method when committing the
     * UnitOfWork failed, too.
     *
     * @param failureCause The exception (or error) causing the roll back
     * @see UnitOfWork#rollback(Throwable)
     */
    void onRollback(Throwable failureCause);

    /**
     * Invoked when an Event is registered for publication when the UnitOfWork is committed. Listeners may alter Event
     * information by returning a new instance for the event. Note that the Listener must ensure the functional meaning
     * of the EventMessage does not change. Typically, this is done by only modifying the MetaData on an Event.
     * <p/>
     * The simplest implementation simply returns the given <code>event</code>.
     *
     * @param event The event about to be registered for publication
     * @param <T>   The type of payload of the EventMessage
     * @return the (modified) event to register for publication
     */
    <T> EventMessage<T> onEventRegistered(EventMessage<T> event);

    /**
     * Invoked before aggregates are committed, and before any events are published. This phase can be used to do
     * validation or other activity that should be able to prevent event dispatching in certain circumstances.
     * <p/>
     * Note that the given <code>events</code> may not contain the uncommitted domain events of each of the
     * <code>aggregateRoots</code>. To retrieve all events, collect all uncommitted events from the aggregate roots and
     * combine them with the list of events.
     *
     * @param aggregateRoots the aggregate roots being committed
     * @param events         Events that have been registered for dispatching with the UnitOfWork
     */
    void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<EventMessage> events);

    /**
     * Notifies listeners that the UnitOfWork is being cleaned up. This gives listeners the opportunity to clean up
     * resources that might have been used during commit or rollback, such as remaining locks, open files, etc.
     * <p/>
     * This method is always called after all listeners have been notified of a commit or rollback.
     */
    void onCleanup();
}
