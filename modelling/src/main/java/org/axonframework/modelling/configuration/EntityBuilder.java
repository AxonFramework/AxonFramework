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

package org.axonframework.modelling.configuration;

import org.axonframework.configuration.ComponentFactory;
import org.axonframework.modelling.repository.AsyncRepository;

/**
 * Interface describing a mechanism to build entities.
 * <p>
 * Implementations of this interface provide a clear path to set all the required components to derive the
 * {@link #entityName()} and {@link #repository()}. The resulting entity {@link AsyncRepository} is registered with the
 * {@link org.axonframework.configuration.ApplicationConfigurer} this builder falls under.
 *
 * @param <I> The type of identifier used to identify the entity that's being built.
 * @param <E> The type of the entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EntityBuilder<I, E> {

    /**
     * The name of the entity being built, typically a concatenation of the entity type's
     * {@link Class#getSimpleName() simple name} and the identifier type's simple name.
     * <p>
     * This entity name is used when registering the {@link #repository()} with the parent
     * {@link org.axonframework.configuration.ApplicationConfigurer}. Hence, when searching for the repository, the
     * result of this method should be used.
     *
     * @return Name of the entity being built.
     */
    String entityName();

    /**
     * The factory to construct the {@link AsyncRepository} providing access to the entity that's being built.
     * <p>
     * The factory is used as is when
     * {@link org.axonframework.configuration.ApplicationConfigurer#registerComponent(Class, String, ComponentFactory)
     * registering} it with the {@link org.axonframework.configuration.ApplicationConfigurer}. During registration, the
     * {@link Class} used is {@code AsyncRepository} and the name used is the result of the {@link #entityName()}
     * method.
     *
     * @return The factory to construct the {@link AsyncRepository} providing access to the entity that's being built.
     */
    ComponentFactory<AsyncRepository<I, E>> repository();
}
