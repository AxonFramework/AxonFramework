/*
 * Copyright (c) 2010-2012. Axon Framework
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
 * Abstract implementation of the {@link UnitOfWorkListener} that provides empty implementation of all methods declared
 * in {@link org.axonframework.unitofwork.UnitOfWorkListener}. This implementation does nothing.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class UnitOfWorkListenerAdapter implements UnitOfWorkListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> EventMessage<T> onEventRegistered(EventMessage<T> event) {
        return event;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit() {
    }

    /**
     * {@inheritDoc}
     *
     * @param failureCause
     */
    @Override
    public void onRollback(Throwable failureCause) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCleanup() {
    }
}
