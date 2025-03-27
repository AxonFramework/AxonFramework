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
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.repository.AsyncRepository;

/**
 * An expansion on the {@link EntityBuilder}, specifically for state-based entities.
 * <p>
 * Invoke the {@code static} {@link #entity(Class, Class)} operation to start the builder flow for a state-based
 * entity.
 * <p>
 * Provides operations to either (1) register a {@link RepositoryPhase#loader(ComponentFactory) entity loader} and
 * {@link PersisterPhase#persister(ComponentFactory) entity persister}, or (2) a
 * {@link RepositoryPhase#repository(ComponentFactory) repository} performing both the loading and persisting task.
 * <p>
 * The separate methods of this builder ensure that the bare minimum required to provide the {@link #entityName()} and
 * {@link #repository()} are present at the end.
 *
 * @param <I> The type of identifier used to identify the state-based entity that's being built.
 * @param <E> The type of the state-based entity being built.
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StateBasedEntityBuilder<I, E> extends EntityBuilder<I, E> {

    /**
     * Starts the builder for a state-based entity with the given {@code entityType} and {@code idType}.
     *
     * @param idType     The type of identifier used to identify the state-based entity that's being built.
     * @param entityType The type of the state-based entity being built.
     * @param <I>        The type of identifier used to identify the state-based entity that's being built.
     * @param <E>        The type of the state-based entity being built.
     * @return The repository phase of this builder, for a fluent API.
     */
    static <I, E> RepositoryPhase<I, E> entity(@Nonnull Class<I> idType,
                                               @Nonnull Class<E> entityType) {
        return new StateBasedEntityBuilderImpl<>(idType, entityType);
    }

    /**
     * The repository phase of the state-based entity builder.
     * <p>
     * Allows for two paths when building a state-based entity. Firstly, a
     * {@link #loader(ComponentFactory) entity loader} can be defined, after which the builder will enforce registration
     * of an {@link PersisterPhase#persister(ComponentFactory) entity persister}. The second option allows for providing
     * a {@link #repository(ComponentFactory) repository} right away.
     *
     * @param <I> The type of identifier used to identify the state-based entity that's being built.
     * @param <E> The type of the state-based entity being built.
     */
    interface RepositoryPhase<I, E> {

        /**
         * Registers the given {@code loader} as a factory method for the state-based entity being built.
         *
         * @param loader A factory method constructing a {@link SimpleRepositoryEntityLoader}.
         * @return The "persister" phase of this builder, for a fluent API.
         */
        PersisterPhase<I, E> loader(@Nonnull ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loader);

        /**
         * Registers the given {@code repository} as a factory method for the state-based entity being built.
         *
         * @param repository A factory method constructing a {@link AsyncRepository}.
         * @return The parent {@link StateBasedEntityBuilder}, signaling the end of configuring a state-based entity.
         */
        StateBasedEntityBuilder<I, E> repository(@Nonnull ComponentFactory<AsyncRepository<I, E>> repository);
    }

    /**
     * The "persister" phase of the state-based entity builder.
     * <p>
     * Enforce providing the {@link #persister(ComponentFactory)} for the state-based entity that's being built.
     *
     * @param <I> The type of identifier used to identify the state-based entity that's being built.
     * @param <E> The type of the state-based entity being built.
     */
    interface PersisterPhase<I, E> {

        /**
         * Registers the given {@code persister} as a factory method for the state-based entity being built.
         *
         * @param persister A factory method constructing a {@link SimpleRepositoryEntityPersister}.
         * @return The parent {@link StateBasedEntityBuilder}, signaling the end of configuring a state-based entity.
         */
        StateBasedEntityBuilder<I, E> persister(
                @Nonnull ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persister
        );
    }
}
