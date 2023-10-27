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

import org.axonframework.common.lock.Lock;
import org.axonframework.messaging.Message;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Aggregate implementation that provides access to the lock held by the aggregate while a command is handled.
 *
 * @param <AR> the aggregate root type
 * @param <A>  the {@link Aggregate} implementation type
 * @author Allard Buijze
 * @since 3.0
 */
public class LockAwareAggregate<AR, A extends Aggregate<AR>> implements Aggregate<AR> {

    private final A wrappedAggregate;
    private final LockSupplier lock;

    /**
     * Initializes a new {@link LockAwareAggregate} for given {@code wrappedAggregate} and {@code lock}.
     *
     * @param wrappedAggregate the aggregate instance to which the LockAwareAggregate will delegate
     * @param lock             the lock held by the aggregate
     */
    public LockAwareAggregate(A wrappedAggregate, Lock lock) {
        this.wrappedAggregate = wrappedAggregate;
        this.lock = () -> lock;
    }

    /**
     * Initializes a new {@link LockAwareAggregate} for given {@code wrappedAggregate} and a supplier of the {@code
     * lock}.
     *
     * @param wrappedAggregate the aggregate instance to which the LockAwareAggregate will delegate
     * @param lock             a supplier of the lock held by the aggregate
     */
    public LockAwareAggregate(A wrappedAggregate, Supplier<Lock> lock) {
        this.wrappedAggregate = wrappedAggregate;
        this.lock = lock::get;
    }

    /**
     * Get the delegate aggregate wrapped by this instance.
     *
     * @return the wrapped aggregate
     */
    public A getWrappedAggregate() {
        return wrappedAggregate;
    }

    /**
     * Check if the aggregate currently holds a lock.
     *
     * @return {@code true} if the lock is held, {@code false} otherwise
     */
    public boolean isLockHeld() {
        return this.lock.acquire().isHeld();
    }

    @Override
    public String type() {
        return wrappedAggregate.type();
    }

    @Override
    public Object identifier() {
        return wrappedAggregate.identifier();
    }

    @Override
    public Long version() {
        return wrappedAggregate.version();
    }

    @Override
    public Object handle(Message<?> message) throws Exception {
        Object result = wrappedAggregate.handle(message);
        // we need to ensure the lock is acquired, as this may not have happened earlier
        lock.acquire();
        return result;
    }

    @Override
    public <R> R invoke(Function<AR, R> invocation) {
        R result = wrappedAggregate.invoke(invocation);
        // we need to ensure the lock is acquired, as this may not have happened earlier
        lock.acquire();

        return result;
    }

    @Override
    public void execute(Consumer<AR> invocation) {
        try {
            wrappedAggregate.execute(invocation);
        } finally {
            lock.acquire();
        }
    }

    @Override
    public boolean isDeleted() {
        return wrappedAggregate.isDeleted();
    }

    @Override
    public Class<? extends AR> rootType() {
        return wrappedAggregate.rootType();
    }

    @FunctionalInterface
    private interface LockSupplier extends Supplier<Lock> {

        default Lock acquire() {
            return this.get();
        }
    }
}
