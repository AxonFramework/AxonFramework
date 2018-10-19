/*
 * Copyright (c) 2010-2018. Axon Framework
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

/**
 * Aggregate implementation that provides access to the lock held by the aggregate while a command is handled.
 *
 * @param <AR> The aggregate root type
 * @param <A>  The {@link Aggregate} implementation type
 */
public class LockAwareAggregate<AR, A extends Aggregate<AR>> implements Aggregate<AR> {

    private final A wrappedAggregate;
    private final Lock lock;

    /**
     * Initializes a new {@link LockAwareAggregate} for given {@code wrappedAggregate} and {@code lock}.
     *
     * @param wrappedAggregate the aggregate instance to which the LockAwareAggregate will delegate
     * @param lock             the lock held by the aggregate
     */
    public LockAwareAggregate(A wrappedAggregate, Lock lock) {
        this.wrappedAggregate = wrappedAggregate;
        this.lock = lock;
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
        return lock.isHeld();
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
        return wrappedAggregate.handle(message);
    }

    @Override
    public <R> R invoke(Function<AR, R> invocation) {
        return wrappedAggregate.invoke(invocation);
    }

    @Override
    public void execute(Consumer<AR> invocation) {
        wrappedAggregate.execute(invocation);
    }

    @Override
    public boolean isDeleted() {
        return wrappedAggregate.isDeleted();
    }

    @Override
    public Class<? extends AR> rootType() {
        return wrappedAggregate.rootType();
    }
}
