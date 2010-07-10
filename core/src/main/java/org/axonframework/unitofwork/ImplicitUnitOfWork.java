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
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a UnitOfWork that is used when no explicit UnitOfWork has been defined. The UnitOfWork
 * automatically commits itself when the last loaded aggregate has been saved. Registered events are published
 * immediately to the event bus.
 * <p/>
 * When an error occurs while committing this UnitOfWork, the "onRollback" will be called listener causing the error,
 * but not on the others. Therefore, this UnitOfWork is not very suitable for commands that deal with multiple
 * aggregates.
 *
 * @author Allard Buijze
 * @since 0.6
 */
class ImplicitUnitOfWork implements UnitOfWork {

    private int managedAggregates = 0;
    private List<UnitOfWorkListener> listeners = new ArrayList<UnitOfWorkListener>();

    @Override
    public <T extends AggregateRoot> void registerAggregate(T aggregateRoot, SaveAggregateCallback<T> repository) {
        managedAggregates++;
    }

    @Override
    public <T extends AggregateRoot> void reportAggregateSaved(T aggregateRoot) {
        managedAggregates--;
        RuntimeException reportedError = null;
        if (managedAggregates <= 0) {
            CurrentUnitOfWork.clear(this);
            for (UnitOfWorkListener listener : listeners) {
                try {
                    listener.afterCommit();
                } catch (RuntimeException e) {
                    listener.onRollback();
                    reportedError = e;
                }
            }
        }
        if (reportedError != null) {
            throw reportedError;
        }
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        listeners.add(listener);
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        eventBus.publish(event);
    }

    @Override
    public void rollback() {
    }

    @Override
    public void commit() {
    }
}
