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

import jakarta.annotation.Nonnull;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.Repository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Simple implementation of the {@link StateManager}. Keeps a list of all registered
 * {@link Repository repositories} and delegates the loading of entities to the appropriate repository. Throws a
 * {@link MissingRepositoryException} if no repository is found for the given entity type and the provided id.
 *
 * @author Mitchell Herrijgers
 * @see StateManager
 * @since 5.0.0
 */
public class SimpleStateManager implements StateManager, DescribableComponent {

    private final String name;
    private final List<Repository<?, ?>> repositories;

    /**
     * Constructs a new simple {@link StateManager} instance with the given {@code name}.
     *
     * @param name The name of the component, used for {@link DescribableComponent describing} the component.
     * @return A {@link Builder} to construct a simple {@link StateManager}.
     */
    public static Builder builder(@Nonnull String name) {
        BuilderUtils.assertNonBlank(name, "Name may not be blank");
        return new Builder(name);
    }

    private SimpleStateManager(@Nonnull Builder builder) {
        this.name = builder.name;
        this.repositories = builder.repositories;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(
            @Nonnull Class<T> entityType,
            @Nonnull I id,
            @Nonnull ProcessingContext context
    ) {
        return repositories
                .stream()
                .filter(r -> r.entityType().isAssignableFrom(entityType))
                .filter(r -> r.idType().isAssignableFrom(id.getClass()))
                .map(r -> (Repository<I, T>) r)
                .findFirst()
                .orElseThrow(() -> new MissingRepositoryException(id.getClass(), entityType))
                .loadOrCreate(id, context)
                .thenApply(me -> {
                    if (me.entity() != null && !entityType.isInstance(me.entity())) {
                        throw new LoadedEntityNotOfExpectedTypeException(me.entity().getClass(), entityType);
                    }
                    return me;
                });
    }

    @Override
    public Set<Class<?>> registeredEntities() {
        return repositories.stream()
                           .map(Repository::entityType)
                           .collect(Collectors.toSet());
    }

    @Override
    public Set<Class<?>> registeredIdsFor(@Nonnull Class<?> entityType) {
        return repositories.stream()
                           .filter(r -> r.entityType().equals(entityType))
                           .map(Repository::idType)
                           .collect(Collectors.toSet());
    }

    @Override
    public <I, T> Repository<I, T> repository(@Nonnull Class<T> entityType, @Nonnull Class<I> idType) {
        //noinspection unchecked
        return (Repository<I, T>) repositories.stream()
                                              .filter(r -> r.entityType().equals(entityType))
                                              .filter(r -> r.idType().equals(idType))
                                              .findFirst()
                                              .orElse(null);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("repositories", repositories);
    }

    /**
     * Builder class for the {@link SimpleStateManager}.
     */
    public static class Builder {

        private final String name;
        private final List<Repository<?, ?>> repositories = new LinkedList<>();

        private Builder(String name) {
            BuilderUtils.assertNonBlank(name, "Name may not be blank");
            this.name = name;
        }

        /**
         * Registers an {@link Repository} for use with this {@link SimpleStateManager}. The combination of
         * {@link Repository#entityType()} and {@link Repository#idType()} must be unique for all registered
         * repositories. If a repository with the same combination is already registered, a
         * {@link ConflictingRepositoryAlreadyRegisteredException} is thrown.
         * <p>
         * The combination of {@link Repository#entityType() entity type} and
         * {@link Repository#idType() id type} of all repositories must be unique and unambigious. This means you
         * can not register a repository if another conflicting repository already exists. If you do, a
         * {@link ConflictingRepositoryAlreadyRegisteredException} will be thrown. Note that superclasses and subclasses
         * of each other are considered conflicting.
         *
         * @param repository The {@link Repository} to use for loading state.
         * @param <I>        The type of id.
         * @param <T>        The type of the entity.
         * @return This instance for fluent interfacing.
         */
        public <I, T> Builder register(Repository<I, T> repository) {
            Optional<Repository<?, ?>> registeredRepository = repositories
                    .stream()
                    .filter(r -> match(r, repository))
                    .findFirst();

            if (registeredRepository.isPresent()) {
                throw new ConflictingRepositoryAlreadyRegisteredException(repository, registeredRepository.get());
            }

            repositories.add(repository);
            return this;
        }

        /**
         * Registers a load and save function for state type {@code T} with id of type {@code I}. Creates a
         * {@link SimpleRepository} for the given type with the given load and save functions.
         * <p>
         * The combination of {@code idType} and {@code entityType} must be unique for all registered repositories,
         * whether registered through this method or {@link #register(Repository)}. If a repository with the same
         * combination is already registered, a {@link ConflictingRepositoryAlreadyRegisteredException} is thrown.
         * <p>
         * The combination of {@link Repository#entityType() entity type} and
         * {@link Repository#idType() id type} of all repositories must be unique and unambigious. This means you
         * can not register a repository if another conflicting repository already exists. If you do, a
         * {@link ConflictingRepositoryAlreadyRegisteredException} will be thrown. Note that superclasses and subclasses
         * of each other are considered conflicting.
         *
         * @param idType     The type of the identifier.
         * @param entityType The type of the state.
         * @param loader     The function to load state.
         * @param persister  The function to persist state.
         * @param <I>        The type of id.
         * @param <T>        The type of state.
         * @return This instance for fluent interfacing.
         */
        public <I, T> Builder register(Class<I> idType,
                                       Class<T> entityType,
                                       SimpleRepositoryEntityLoader<I, T> loader,
                                       SimpleRepositoryEntityPersister<I, T> persister
        ) {
            return register(new SimpleRepository<>(idType, entityType, loader, persister));
        }

        public SimpleStateManager build() {
            return new SimpleStateManager(this);
        }

        /**
         * Checks if the given repositories match based on their entity and id types. Types match if it's the same type
         * or if one type is a superclass of the other. This ensures that there are no conflicts when loading entities.
         * For any id and entity type combination, only one repository should exist.
         */
        private boolean match(Repository<?, ?> repositoryOne, Repository<?, ?> repositoryTwo) {
            return matchesBasedOnEntityType(repositoryOne, repositoryTwo) && matchesBasedOnIdType(repositoryOne,
                                                                                                  repositoryTwo);
        }

        private static boolean matchesBasedOnIdType(Repository<?, ?> repositoryOne,
                                                    Repository<?, ?> repositoryTwo) {
            return repositoryOne.idType().isAssignableFrom(repositoryTwo.idType())
                    || repositoryTwo.idType().isAssignableFrom(repositoryOne.idType());
        }

        private static boolean matchesBasedOnEntityType(Repository<?, ?> repositoryOne,
                                                        Repository<?, ?> repositoryTwo) {
            return repositoryOne.entityType().isAssignableFrom(repositoryTwo.entityType())
                    || repositoryTwo.entityType().isAssignableFrom(repositoryOne.entityType());
        }
    }
}
