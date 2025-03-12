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
 * Exception thrown by the {@link StateManager} when the loaded entity from the {@link AsyncRepository} is not
 * assignable to the expected type. This can happen when the entity is loaded from a repository that is not
 * parameterized correctly, or when a subclass of an entity was attempted to be loaded and the repository returns a less
 * specific type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class LoadedEntityNotOfExpectedTypeException extends RuntimeException {

    /**
     * Initialize the exception with a message containing entity type that was loaded and the type that was expected
     *
     * @param entityType   The type of the entity that was loaded and is not assignable to the expected type.
     * @param expectedType The type that was expected.
     */
    public LoadedEntityNotOfExpectedTypeException(Class<?> entityType, Class<?> expectedType) {
        super("The loaded entity of type [%s] is not assignable to type [%s].".formatted(
                entityType.getName(),
                expectedType.getName()
        ));
    }
}
