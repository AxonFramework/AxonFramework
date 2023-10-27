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

package org.axonframework.modelling.command.inspection;

import java.util.Collections;
import java.util.Set;

/**
 * Interface of a factory for an {@link AggregateModel} for any given type defining an aggregate.
 */
public interface AggregateMetaModelFactory {

    /**
     * Create an Aggregate meta model for the given {@code aggregateType}. The meta model will inspect the capabilities
     * and characteristics of the given type.
     *
     * @param aggregateType The Aggregate class to be inspected
     * @param <T>           The Aggregate type
     * @return Model describing the capabilities and characteristics of the inspected Aggregate class
     */
    default <T> AggregateModel<T> createModel(Class<? extends T> aggregateType) {
        return createModel(aggregateType, Collections.emptySet());
    }

    /**
     * Create an Aggregate meta model for the given {@code aggregateType} and provided {@code subtypes}. The meta model
     * will inspect the capabilities and characteristics of the given {@code aggregateType} and its {@code subtypes}.
     *
     * @param aggregateType The Aggregate class to be inspected
     * @param subtypes      Subtypes of this Aggregate class
     * @param <T>           The Aggregate type
     * @return Model describing the capabilities and characteristics of the inspected Aggregate class and its subtypes
     */
    <T> AggregateModel<T> createModel(Class<? extends T> aggregateType, Set<Class<? extends T>> subtypes);
}
