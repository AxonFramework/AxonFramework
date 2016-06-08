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

import java.util.concurrent.Callable;

/**
 * The repository provides an abstraction of the storage of aggregates.
 *
 * @param <T> The type of aggregate this repository stores.
 */
public interface Repository<T> {

    /**
     * Load the aggregate with the given unique identifier. No version checks are done when loading an aggregate,
     * meaning that concurrent access will not be checked for.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @return The aggregate root with the given identifier.
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    Aggregate<T> load(String aggregateIdentifier);

    /**
     * Load the aggregate with the given unique identifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return The aggregate root with the given identifier.
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    Aggregate<T> load(String aggregateIdentifier, Long expectedVersion);

    /**
     * Creates a new managed instance for the aggregate, using the given {@code factoryMethod}
     * to instantiate the aggregate's root.
     *
     * @param factoryMethod The method to create the aggregate's root instce
     * @return an Aggregate instance describing the aggregate's state
     * @throws Exception when the factoryMethod throws an exception
     */
    Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception;
}
