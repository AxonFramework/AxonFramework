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

public abstract class SagaLifecycle {

    private static final ThreadLocal<SagaLifecycle> CURRENT_SAGA_LIFECYCLE = new ThreadLocal<>();

    public static void associateWith(String associationKey, String associationValue) {
        associateWith(new AssociationValue(associationKey, associationValue));
    }

    public static void associateWith(String associationKey, Number associationValue) {
        associateWith(new AssociationValue(associationKey, associationValue.toString()));
    }

    public static void associateWith(AssociationValue associationValue) {
        getInstance().doAssociateWith(associationValue);
    }

    public static void removeAssociationWith(String associationKey, String associationValue) {
        getInstance().doRemoveAssociation(new AssociationValue(associationKey, associationValue));
    }

    public static void removeAssociationWith(String associationKey, Number associationValue) {
        removeAssociationWith(associationKey, associationValue.toString());
    }

    public static void end() {
        getInstance().doEnd();
    }

    protected abstract void doEnd();

    protected abstract void doRemoveAssociation(AssociationValue associationValue);

    protected abstract void doAssociateWith(AssociationValue associationValue);

    protected static SagaLifecycle getInstance() {
        SagaLifecycle instance = CURRENT_SAGA_LIFECYCLE.get();
        if (instance == null) {
            throw new IllegalStateException("Cannot retrieve current SagaLifecycle; none is yet defined");
        }
        return instance;
    }

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
