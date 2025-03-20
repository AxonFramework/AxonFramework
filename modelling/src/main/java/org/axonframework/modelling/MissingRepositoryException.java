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

package org.axonframework.modelling;

import org.axonframework.modelling.repository.AsyncRepository;

/**
 * Exception thrown by the {@link StateManager} when no {@link AsyncRepository} is registered for a given state type.
 * Can be resolved by registering a {@link AsyncRepository} for the given state type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class MissingRepositoryException extends RuntimeException {

    /**
     * Initialize the exception with a message containing the given state type.
     *
     * @param entityType The state type for which no repository was registered.
     */
    public MissingRepositoryException(Class<?> idType, Class<?> entityType) {
        super("No repository was registered for the given entity type [%s] with id type [%s]".formatted(
                entityType.getName(),
                idType.getName()
        ));
    }
}
