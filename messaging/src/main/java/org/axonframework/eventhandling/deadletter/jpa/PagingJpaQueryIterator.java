/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.common.transaction.TransactionManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.persistence.TypedQuery;

/**
 * Enables iterating through a JPA query using paging while lazily mapping the results when necessary.
 * <p>
 * Do not use this for paging when you care about concurrent deletes.
 * <p>
 * TODO: Talk about Steven whether this is useful and/or necessary
 *
 * @param <T> The query result type.
 * @param <R> The mapped result type.
 */
public class PagingJpaQueryIterator<T, R> implements Iterator<R>, Iterable<R> {

    private final int pageSize;
    private final Supplier<TypedQuery<T>> querySupplier;
    private final Deque<T> queue = new ArrayDeque<>();
    private final TransactionManager transactionManager;
    private final Function<T, R> lazyMappingFunction;
    private int page = 0;

    PagingJpaQueryIterator(int pageSize, TransactionManager transactionManager, Supplier<TypedQuery<T>> querySupplier) {
        this.pageSize = pageSize;
        this.transactionManager = transactionManager;
        this.querySupplier = querySupplier;
        //noinspection unchecked
        this.lazyMappingFunction = (t) -> (R) t;
    }

    PagingJpaQueryIterator(int pageSize, TransactionManager transactionManager, Supplier<TypedQuery<T>> querySupplier,
                           Function<T, R> lazyMappingFunction) {
        this.pageSize = pageSize;
        this.transactionManager = transactionManager;
        this.querySupplier = querySupplier;
        this.lazyMappingFunction = lazyMappingFunction;
    }


    @Override
    public boolean hasNext() {
        refreshPageIfNecessary();
        return !queue.isEmpty();
    }

    @Override
    public R next() {
        refreshPageIfNecessary();
        T pop = queue.pop();
        if (pop == null) {
            throw new NoSuchElementException();
        }
        return lazyMappingFunction.apply(pop);
    }

    private void refreshPageIfNecessary() {
        if (!queue.isEmpty()) {
            return;
        }
        this.transactionManager.executeInTransaction(() -> {
            querySupplier.get().setMaxResults(pageSize)
                         .setFirstResult(page * pageSize)
                         .getResultList()
                         .forEach(queue::offerLast);
        });
        page++;
    }

    @Override
    public Iterator<R> iterator() {
        return this;
    }
}
