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

/**
 * Special case of the unit of work, that clears itself when all aggregates have been committed. This removed the need
 * for an interceptor or other mechanism to clean up the UnitOfWork after committing.
 * <p/>
 * The ImplicitUnitOfWork will also force all generated events to be dispatched immediately to the event bus.
 *
 * @author Allard Buijze
 * @since 0.6
 */
class ImplicitUnitOfWork extends DefaultUnitOfWork {

    private int managedAggregates = 0;

    @Override
    public void commitAggregate(AggregateRoot aggregate) {
        try {
            super.commitAggregate(aggregate);
        } finally {
            managedAggregates--;
            if (managedAggregates == 0) {
                clear();
            }
        }
    }

    @Override
    public <T extends AggregateRoot> void registerAggregate(T aggregate, Long expectedVersion,
                                                            SaveAggregateCallback<T> callback) {
        super.registerAggregate(aggregate, expectedVersion, callback);
        managedAggregates++;
    }

    @Override
    public void rollback() {
        try {
            super.rollback();
        } finally {
            clear();
        }
    }

    @Override
    public void commit() {
        try {
            super.commit();
        } finally {
            clear();
        }
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        eventBus.publish(event);
    }

    private void clear() {
        CurrentUnitOfWork.clear(this);
    }
}
