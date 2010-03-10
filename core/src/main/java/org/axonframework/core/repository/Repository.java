/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository;

import org.axonframework.core.AggregateRoot;

import java.util.UUID;

/**
 * The repository provides an abstraction of the storage of aggregates.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores.
 * @since 0.1
 */
public interface Repository<T extends AggregateRoot> {

    /**
     * Store the given aggregate. If an aggregate with the same unique identifier already exists, it is updated
     * instead.
     *
     * @param aggregate The aggregate root of the aggregate to store.
     */
    void save(T aggregate);

    /**
     * Load the aggregate with the given unique identifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load
     * @return The aggregate root with the given identifier.
     *
     * @throws org.axonframework.core.AggregateNotFoundException
     *          if aggregate with given id cannot be found
     */
    T load(UUID aggregateIdentifier);

    /**
     * Delete the aggregate with the given unique identifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to delete
     */
    void delete(UUID aggregateIdentifier);
}
