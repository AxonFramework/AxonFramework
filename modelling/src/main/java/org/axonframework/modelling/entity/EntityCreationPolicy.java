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

package org.axonframework.modelling.entity;

/**
 * Determines how entities should be loaded or created when handling a command. By default, the
 * {@link #CREATE_IF_MISSING} policy is used, which means that a new entity will be created if it does not exist yet.
 *
 * <h4>Before 5.0.0</h4>
 * This enum was called {@code org.axonframework.modelling.command.AggregateCreationPolicy} and was used to
 * determine the creation policy of aggregates. It has been renamed to reflect the move to entities in Axon 5.0.0.
 * In all other aspects, it remains the same
 *
 * @author Marc Gathier
 * @author Mitchell Herrijgers
 * @since 4.3
 */
public enum EntityCreationPolicy {
    /**
     * Always create a new instance of the entity. Will not check the persistence storage for an existing instance, and
     * as such might throw an exception if the entity already exists and the transaction is committed.
     */
    ALWAYS,

    /**
     * Create a new instance of the entity when it is not found. This is the default behavior. This will check the
     * persistence storage for an existing instance, and will create a new one if it does not exist.
     */
    CREATE_IF_MISSING,

    /**
     * Expect instance of the entity to exist. Will not create a new instance if it does not exist, and will throw an
     * exception if it does not.
     */
    NEVER
}
