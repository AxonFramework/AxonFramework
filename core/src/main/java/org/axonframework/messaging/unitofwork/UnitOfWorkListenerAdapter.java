/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.EventMessage;

import java.util.List;
import java.util.Set;

/**
 * Abstract implementation of the {@link UnitOfWorkListener} that provides empty implementation of all methods declared
 * in {@link UnitOfWorkListener}. This implementation does nothing.
 *
 * @author Allard Buijze
 * @since 0.6
 */
@Deprecated
public abstract class UnitOfWorkListenerAdapter implements UnitOfWorkListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> EventMessage<T> onEventRegistered(UnitOfWork unitOfWork, EventMessage<T> event) {
        return event;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
    }

    @Override
    public void onPrepareTransactionCommit(UnitOfWork unitOfWork, Object transaction) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit(UnitOfWork unitOfWork) {
    }

    /**
     * {@inheritDoc}
     *
     * @param failureCause
     */
    @Override
    public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCleanup(UnitOfWork unitOfWork) {
    }
}
