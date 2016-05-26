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

package org.axonframework.saga;

import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ProcessingToken;

import java.util.function.Consumer;
import java.util.function.Function;

public class LockingSaga<T> implements Saga<T> {

    private final LockFactory lockFactory;
    private final Saga<T> delegate;

    public LockingSaga(LockFactory lockFactory, Saga<T> delegate) {
        this.lockFactory = lockFactory;
        this.delegate = delegate;
    }

    @Override
    public String getSagaIdentifier() {
        return delegate.getSagaIdentifier();
    }

    @Override
    public AssociationValues getAssociationValues() {
        return delegate.getAssociationValues();
    }

    @Override
    public <R> R invoke(Function<T, R> invocation) {
        try (Lock ignored = lockFactory.obtainLock(delegate.getSagaIdentifier())) {
            return delegate.invoke(invocation);
        }
    }

    @Override
    public void execute(Consumer<T> invocation) {
        try (Lock ignored = lockFactory.obtainLock(delegate.getSagaIdentifier())) {
            delegate.execute(invocation);
        }
    }

    @Override
    public boolean handle(EventMessage<?> event) {
        try (Lock ignored = lockFactory.obtainLock(delegate.getSagaIdentifier())) {
            return delegate.handle(event);
        }
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public ProcessingToken processingToken() {
        return delegate.processingToken();
    }

}
