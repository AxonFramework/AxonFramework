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
 * Enables iterating through a JPA query using paging while lazily mapping the results when necessary. Paging is taken
 * care of automatically, fetching the next page when the items run out to iterate through.
 * <p>
 * Do not use this for paging when you care about concurrent deletes. If you loaded a page, delete an item from it, and
 * load the next, you will miss an item during iteration.
 * <p>
 * The {@link #iterator()} function can be called multiple times to loop through the items, restarting the query from
 * the start.
 *
 * @param <T> The query result type.
 * @param <R> The mapped result type.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class PagingJpaQueryIterable<T, R> implements Iterable<R> {

    private final int pageSize;
    private final Supplier<TypedQuery<T>> querySupplier;
    private final TransactionManager transactionManager;
    private final Function<T, R> lazyMappingFunction;

    /**
     * Constructs a new {@link Iterable} using the provided {@code querySupplier} to construct queries when a new page
     * needs to be fetched. Items are lazily mapped by the provided {@code lazyMappingFunction} when iterating.
     *
     * @param pageSize            The size of the pages.
     * @param transactionManager  The {@link TransactionManager} to use when fetching items.
     * @param querySupplier       The supplier of the queries. Will be invoked for each page.
     * @param lazyMappingFunction The mapping function to map items to the desired representation.
     */
    public PagingJpaQueryIterable(int pageSize,
                                  TransactionManager transactionManager,
                                  Supplier<TypedQuery<T>> querySupplier,
                                  Function<T, R> lazyMappingFunction
    ) {
        this.pageSize = pageSize;
        this.transactionManager = transactionManager;
        this.querySupplier = querySupplier;
        this.lazyMappingFunction = lazyMappingFunction;
    }

    /**
     * The {@link Iterator} that loops through the provided query's pages until it runs out of items.
     */
    public class PagingIterator implements Iterator<R> {
        private final Deque<T> queue = new ArrayDeque<>();
        private int page = 0;

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
            transactionManager.executeInTransaction(() -> querySupplier
                    .get()
                    .setMaxResults(pageSize)
                    .setFirstResult(page * pageSize)
                    .getResultList()
                    .forEach(queue::offerLast));
            page++;
        }
    }

    @Override
    public Iterator<R> iterator() {
        return new PagingIterator();
    }
}
