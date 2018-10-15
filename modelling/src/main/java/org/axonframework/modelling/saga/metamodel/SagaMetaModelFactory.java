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

package org.axonframework.modelling.saga.metamodel;

/**
 * Interface of a factory for a {@link SagaModel} for any given saga type.
 */
public interface SagaMetaModelFactory {

    /**
     * Create a saga meta model for the given {@code sagaType}. The meta model will inspect the capabilities and
     * characteristics of the given type.
     *
     * @param sagaType The saga class to be inspected
     * @param <T> The saga type
     * @return Model describing the capabilities and characteristics of the inspected saga class
     */
    <T> SagaModel<T> modelOf(Class<T> sagaType);
}
