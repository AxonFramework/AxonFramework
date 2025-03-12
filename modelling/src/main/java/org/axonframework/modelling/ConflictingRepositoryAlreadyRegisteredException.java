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

import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;

/**
 * Exception thrown by the {@link StateManager} when trying to register an {@link AsyncRepository} for an entity type
 * and id type combination that already has a repository registered.
 * When registering super- or subtypes of an already registered type, this will also be considered an already registered
 * repository. By guarding this, the {@link StateManager#loadManagedEntity(Class, Object, ProcessingContext)} will
 * unambiguously resolve the correct repository to load the entity from and prevent runtime errors.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ConflictingRepositoryAlreadyRegisteredException extends RuntimeException {

    /**
     * Initialize the exception with a message that contains the types of the conflicting repositories.
     *
     * @param repository The repository that was attempted to be registered.
     * @param existingRepository The repository that was already registered for the conflicting entity type.
     */
    public ConflictingRepositoryAlreadyRegisteredException(AsyncRepository<?, ?> repository,
                                                           AsyncRepository<?, ?> existingRepository) {
        super("Cannot register repository for state type [%s] with id type [%s] as conflicting repository for entity type [%s] with [%s] type was already registered.".formatted(
                repository.entityType().getName(),
                repository.idType().getName(),
                existingRepository.entityType().getName(),
                existingRepository.idType().getName()
        ));
    }
}
