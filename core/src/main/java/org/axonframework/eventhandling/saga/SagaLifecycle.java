/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import java.util.concurrent.Callable;

/**
 * Abstract base class of a component that models a saga's life cycle.
 */
public abstract class SagaLifecycle {

    private static final ThreadLocal<SagaLifecycle> CURRENT_SAGA_LIFECYCLE = new ThreadLocal<>();

    /**
     * Registers a AssociationValue with the currently active saga. When the saga is committed, it can be found using
     * the registered property. If the saga already has the given association, nothing happens.
     *
     * @param associationKey   The key of the association value to associate this saga with.
     * @param associationValue The value of the association value to associate this saga with.
     */
    public static void associateWith(String associationKey, String associationValue) {
        associateWith(new AssociationValue(associationKey, associationValue));
    }

    /**
     * Registers a AssociationValue with the currently active saga. When the saga is committed, it can be found using
     * the registered property. The number value will be converted to a string. If the saga already has the given
     * association, nothing happens.
     *
     * @param associationKey   The key of the association value to associate this saga with.
     * @param associationValue The value of the association value to associate this saga with.
     */
    public static void associateWith(String associationKey, Number associationValue) {
        associateWith(new AssociationValue(associationKey, associationValue.toString()));
    }

    /**
     * Registers a AssociationValue with the currently active saga. When the saga is committed, it can be found using
     * the registered property. If the saga already has the given association, nothing happens.
     *
     * @param associationValue The association to associate this saga with.
     */
    public static void associateWith(AssociationValue associationValue) {
        getInstance().doAssociateWith(associationValue);
    }

    /**
     * Removes the given association from the currently active Saga. When the saga is committed, it can no longer be
     * found using the given association value. If the given saga wasn't associated with given values, nothing happens.
     *
     * @param associationKey   The key of the association value to remove from this saga.
     * @param associationValue The value of the association value to remove from this saga.
     */
    public static void removeAssociationWith(String associationKey, String associationValue) {
        getInstance().doRemoveAssociation(new AssociationValue(associationKey, associationValue));
    }

    /**
     * Removes the given association from the currently active Saga. When the saga is committed, it can no longer be
     * found using the given association value. If the given saga wasn't associated with given values, nothing happens.
     * The number value will be converted to a string.
     *
     * @param associationKey   The key of the association value to remove from this saga.
     * @param associationValue The value of the association value to remove from this saga.
     */
    public static void removeAssociationWith(String associationKey, Number associationValue) {
        removeAssociationWith(associationKey, associationValue.toString());
    }

    /**
     * Marks the saga as ended. Ended saga's may be cleaned up by the repository when they are committed.
     */
    public static void end() {
        getInstance().doEnd();
    }

    /**
     * {@link SagaLifecycle} instance method to mark the current saga as ended.
     */
    protected abstract void doEnd();

    /**
     * {@link SagaLifecycle} instance method to remove the given {@code associationValue}. If the current saga is not
     * associated with given value, this should do nothing.
     *
     * @param associationValue the association value to remove
     */
    protected abstract void doRemoveAssociation(AssociationValue associationValue);

    /**
     * {@link SagaLifecycle} instance method to register the given {@code associationValue}. If the current saga is
     * already associated with given value, this should do nothing.
     *
     * @param associationValue the association value to add
     */
    protected abstract void doAssociateWith(AssociationValue associationValue);

    /**
     * Get the current {@link SagaLifecycle} instance for the current thread. If none exists an {@link
     * IllegalStateException} is thrown.
     *
     * @return the thread's current {@link SagaLifecycle}
     */
    protected static SagaLifecycle getInstance() {
        SagaLifecycle instance = CURRENT_SAGA_LIFECYCLE.get();
        if (instance == null) {
            throw new IllegalStateException("Cannot retrieve current SagaLifecycle; none is yet defined");
        }
        return instance;
    }

    /**
     * {@link SagaLifecycle} instance method to execute given {@code task} in the context of this SagaLifeCycle. This
     * updates the thread's current saga lifecycle before executing the task. If a lifecycle is already registered with
     * the current thread that one will be temporarily replaced with this lifecycle until the task completes. This
     * method returns the execution result of the task.
     *
     * @param task the task to execute
     * @param <V> the type of execution result of the task
     * @return the execution result
     * @throws Exception if executing the task results in an exception
     */
    protected <V> V executeWithResult(Callable<V> task) throws Exception {
        SagaLifecycle existing = CURRENT_SAGA_LIFECYCLE.get();
        CURRENT_SAGA_LIFECYCLE.set(this);
        try {
            return task.call();
        } finally {
            if (existing == null) {
                CURRENT_SAGA_LIFECYCLE.remove();
            } else {
                CURRENT_SAGA_LIFECYCLE.set(existing);
            }
        }
    }

    /**
     * {@link SagaLifecycle} instance method to execute given {@code task} in the context of this SagaLifeCycle. This
     * updates the thread's current saga lifecycle before executing the task. If a lifecycle is already registered with
     * the current thread that one will be temporarily replaced with this lifecycle until the task completes.
     *
     * @param task the task to execute
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
            throw new SagaExecutionException("Exception while executing a task for a saga", e);
        }
    }
}
