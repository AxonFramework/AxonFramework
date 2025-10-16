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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;

/**
 * A {@link ConfigurationEnhancer} implementation that will configure an Aggregate with the Axon
 * {@link org.axonframework.configuration.Configuration}.
 *
 * @param <T>  The type of Aggregate to configure
 * @param <ID> The type of Aggregate id to configure
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 4.6.0
 */
@Internal
public class SpringEventSourcedEntityConfigurer<ID, T> implements ConfigurationEnhancer {

    private final Class<T> entityType;
    private final Class<ID> idType;

    /**
     * Initializes a {@link ConfigurationEnhancer} for given {@code entityType} and {@code idType}.
     *
     * @param entityType The declared type of the entity.
     * @param idType        The type of id.
     */
    public SpringEventSourcedEntityConfigurer(@Nonnull Class<T> entityType, @Nonnull Class<ID> idType) {
        this.entityType = entityType;
        this.idType = idType;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        var eventSourcedEntityModule = EventSourcedEntityModule.annotated(this.idType, this.entityType);
        registry.registerModule(eventSourcedEntityModule);
    }
}
