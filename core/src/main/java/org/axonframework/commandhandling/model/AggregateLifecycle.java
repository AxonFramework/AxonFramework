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

import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class AggregateLifecycle {

    private static final ThreadLocal<AggregateLifecycle> CURRENT = new ThreadLocal<>();

    public static ApplyMore doApply(Object payload) {
        return AggregateLifecycle.getInstance().doApply(payload, MetaData.emptyInstance());
    }

    public static ApplyMore apply(Object payload, MetaData metaData) {
        return AggregateLifecycle.getInstance().doApply(payload, metaData);
    }

    public static ApplyMore apply(Object payload) {
        return AggregateLifecycle.getInstance().doApply(payload, MetaData.emptyInstance());
    }

    public static void markDeleted() {
        getInstance().doMarkDeleted();
    }

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

    protected abstract void doMarkDeleted();

    protected void registerWithUnitOfWork() {
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
        HashSet<AggregateLifecycle> managedAggregates = unitOfWork.getOrComputeResource("ManagedAggregates", k -> new HashSet<>());
        managedAggregates.add(this);
    }

    protected abstract <T> ApplyMore doApply(T payload, MetaData metaData);

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
