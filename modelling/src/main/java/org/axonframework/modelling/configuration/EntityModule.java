/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Module;
import org.axonframework.modelling.repository.Repository;

/**
 * {@link Module} that builds an entity of type {@code E} with an identifier of type {@code ID}. This entity is then
 * registered to the nearest parent {@link org.axonframework.modelling.StateManager} with the created
 * {@link Repository}.
 * <p>
 * Make sure to register this module with the parent module or configurer of your choice. Each entity should only have
 * one module, and each module should only be registered once. As such, make sure to register it on the right level of
 * your module hierarchy.
 *
 * @param <ID> The type of the entity's identifier.
 * @param <E>  The type of the entity.
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityModule<ID, E> extends Module {

    /**
     * The name of the entity, typically a concatenation of the entity type's {@link Class#getSimpleName() simple name}
     * and the identifier type's simple name.
     * <p>
     * The module should use this name for the components they register to the
     * {@link ComponentRegistry}.
     *
     * @return The name of the entity.
     */
    default String entityName() {
        return String.format("%s#%s", entityType().getName(), idType().getName());
    }

    /**
     * Returns the type of the entity's identifier.
     *
     * @return The type of the entity's identifier.
     */
    Class<ID> idType();

    /**
     * Returns the type of the entity.
     *
     * @return The type of the entity.
     */
    Class<E> entityType();
}
