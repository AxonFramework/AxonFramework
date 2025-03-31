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

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.modelling.SimpleRepository;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.repository.AsyncRepository;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link StateBasedEntityBuilder}.
 *
 * @param <I> The type of identifier used to identify the state-based entity that's being built.
 * @param <E> The type of the state-based entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultStateBasedEntityBuilder<I, E> implements
        StateBasedEntityBuilder<I, E>,
        StateBasedEntityBuilder.RepositoryPhase<I, E>,
        StateBasedEntityBuilder.PersisterPhase<I, E> {

    private final Class<I> idType;
    private final Class<E> entityType;
    private ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loaderFactory;
    private ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persisterFactory;
    private ComponentFactory<AsyncRepository<I, E>> repositoryFactory;

    DefaultStateBasedEntityBuilder(@Nonnull Class<I> idType,
                                   @Nonnull Class<E> entityType) {
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public StateBasedEntityBuilder<I, E> persister(
            @Nonnull ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persister
    ) {
        persisterFactory = requireNonNull(persister, "The repository persister factory cannot be null.");
        return this;
    }

    @Override
    public PersisterPhase<I, E> loader(@Nonnull ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loader) {
        loaderFactory = requireNonNull(loader, "The repository loader factory cannot be null.");
        return this;
    }

    @Override
    public StateBasedEntityBuilder<I, E> repository(
            @Nonnull ComponentFactory<AsyncRepository<I, E>> repository
    ) {
        repositoryFactory = requireNonNull(repository, "The repository factory cannot be null.");
        return this;
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public ComponentFactory<AsyncRepository<I, E>> repository() {
        return repositoryFactory != null
                ? repositoryFactory
                : c -> new SimpleRepository<>(idType,
                                              entityType,
                                              loaderFactory.build(c),
                                              persisterFactory.build(c));
    }
}
