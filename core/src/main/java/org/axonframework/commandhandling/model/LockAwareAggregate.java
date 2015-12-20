/*
 * Copyright (c) 2010-2015. Axon Framework
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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.lock.Lock;

import java.util.function.Consumer;
import java.util.function.Function;

public class LockAwareAggregate<T, A extends Aggregate<T>> implements Aggregate<T> {

    private final A wrappedAggregate;
    private final Lock lock;

    public LockAwareAggregate(A wrappedAggregate, Lock lock) {
        this.wrappedAggregate = wrappedAggregate;
        this.lock = lock;
    }

    public A getWrappedAggregate() {
        return wrappedAggregate;
    }

    public boolean isLockHeld() {
        return lock.isHeld();
    }

    @Override
    public String identifier() {
        return wrappedAggregate.identifier();
    }

    @Override
    public Long version() {
        return wrappedAggregate.version();
    }

    @Override
    public Object handle(CommandMessage<?> msg) {
        return wrappedAggregate.handle(msg);
    }

    @Override
    public <R> R map(Function<T, R> invocation) {
        return wrappedAggregate.map(invocation);
    }

    @Override
    public void execute(Consumer<T> invocation) {
        wrappedAggregate.execute(invocation);
    }

    @Override
    public boolean isDeleted() {
        return wrappedAggregate.isDeleted();
    }

    @Override
    public Class<? extends T> rootType() {
        return wrappedAggregate.rootType();
    }
}
