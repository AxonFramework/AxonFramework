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

package org.axonframework.modelling.command.inspection;

/**
 * Specialized EntityModel that describes the capabilities and properties of an aggregate root of type {@code T}.
 *
 * @param <T> The type of the aggregate root
 */
public interface AggregateModel<T> extends EntityModel<T> {

    /**
     * Get the String representation of the modeled aggregate's type. This defaults to the simple name of the
     * aggregate's type.
     *
     * @return The type of the aggregate
     */
    String type();

    /**
     * Get the current version number of the given {@code aggregate}. For event sourced aggregates this is identical to
     * the sequence number of the last applied event.
     *
     * @param target The target aggregate root instance
     * @return The current version of the aggregate
     */
    Long getVersion(T target);
}
