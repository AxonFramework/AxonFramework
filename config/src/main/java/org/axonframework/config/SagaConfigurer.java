/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.config;

import jakarta.annotation.Nonnull;
import org.axonframework.modelling.saga.repository.SagaStore;

/**
 * A configurer for saga registration that allows customization of saga behavior.
 *
 * @param <T> The type of the saga being configured.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SagaConfigurer<T> {

    private final Class<T> sagaType;
    private String processingGroup;
    private SagaStore<Object> sagaStore;

    /**
     * Creates a new {@code SagaConfigurer} for the given saga type.
     *
     * @param sagaType The type of the saga being configured.
     */
    public SagaConfigurer(@Nonnull Class<T> sagaType) {
        this.sagaType = sagaType;
    }

    /**
     * Configures the processing group for this saga.
     *
     * @param processingGroup The processing group name.
     * @return This configurer for a fluent API.
     */
    public SagaConfigurer<T> processingGroup(@Nonnull String processingGroup) {
        this.processingGroup = processingGroup;
        return this;
    }

    /**
     * Configures the saga store for this saga.
     *
     * @param sagaStore The saga store to use.
     * @return This configurer for a fluent API.
     */
    public SagaConfigurer<T> sagaStore(@Nonnull SagaStore<Object> sagaStore) {
        this.sagaStore = sagaStore;
        return this;
    }

    /**
     * Gets the saga type.
     *
     * @return The saga type.
     */
    public Class<T> getSagaType() {
        return sagaType;
    }

    /**
     * Gets the configured processing group.
     *
     * @return The processing group, or null if not configured.
     */
    public String getProcessingGroup() {
        return processingGroup;
    }

    /**
     * Gets the configured saga store.
     *
     * @return The saga store, or null if not configured.
     */
    public SagaStore<Object> getSagaStore() {
        return sagaStore;
    }
}
