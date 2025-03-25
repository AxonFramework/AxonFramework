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
import org.axonframework.configuration.Module;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.repository.AsyncRepository;

/**
 * A {@link Module} implementation providing operation to construct a stateful command handling application module.
 * <p>
 * The {@code StatefulCommandHandlingModule} follows a builder paradigm, wherein several entities and
 * {@link StatefulCommandHandler StatefulCommandHandlers} can be registered in either order.
 * <p>
 * When {@link Builder#withEntity(Class, Class) registering entities}, the entity's type and identifier type are
 * expected first. From there, either a {@link AsyncRepository} or a separate {@link SimpleRepositoryEntityLoader} and
 * {@link SimpleRepositoryEntityPersister} are expected. The {@link EntitiesOrHandlerPhase#andEntity(Class, Class)} can
 * then be used to add another entity. When all entities have been registered, you can switch to
 * {@link EntitiesOrHandlerPhase#withHandler(QualifiedName, StatefulCommandHandler) start registering command
 * handlers}.
 * <p>
 * To register stateful command handlers, the {@link Builder#withHandler(QualifiedName, StatefulCommandHandler)} is
 * used. If the construction of the {@code StatefulCommandHandler} requires components from the
 * {@link org.axonframework.configuration.NewConfiguration}, the
 * {@link Builder#withHandler(QualifiedName, ComponentFactory)} operation should be used instead.
 *
 * TODO validate/finalize JavaDoc
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StatefulCommandHandlingModule extends Module<StatefulCommandHandlingModule> {

    /**
     * Starts a {@code StatefulCommandHandlingModule} module with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code StatefulCommandHandlingModule} under construction.
     * @return A builder for a {@code StatefulCommandHandlingModule}.
     */
    static Builder module(String moduleName) {
        return new StatefulCommandHandlingModuleImpl(moduleName);
    }

    /**
     *
     */
    interface Builder {

        /**
         * @param commandName
         * @param commandHandler
         * @return
         */
        // TODO DISCUSS - have an explicit handlers() method to start with handlers?
        HandlersOrEntityPhase withHandler(@Nonnull QualifiedName commandName,
                                          @Nonnull StatefulCommandHandler commandHandler);

        /**
         * @param commandName
         * @param commandHandlerBuilder
         * @return
         */
        HandlersOrEntityPhase withHandler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder
        );

        /**
         * @param idType
         * @param entityType
         * @param <ID>
         * @param <T>
         * @return
         */
        // TODO DISCUSS - have an explicit entities() method to start with entities?
        <ID, T> RepositoryPhase<ID, T> withEntity(@Nonnull Class<ID> idType,
                                                  @Nonnull Class<T> entityType);

        // TODO DISCUSS - Do we want a withStateManager method? This should replace the entity flow entirely I think.
    }

    /**
     * @param <ID>
     * @param <T>
     */
    interface RepositoryPhase<ID, T> {

        /**
         * @param loader
         * @return
         */
        PersisterPhase<ID, T> withLoader(
                @Nonnull ComponentFactory<SimpleRepositoryEntityLoader<ID, T>> loader
        );

        /**
         * @param repository
         * @return
         */
        EntitiesOrHandlerPhase withRepository(
                @Nonnull ComponentFactory<AsyncRepository<ID, T>> repository
        );
    }

    /**
     * @param <ID>
     * @param <T>
     */
    interface PersisterPhase<ID, T> {

        /**
         * @param persister
         * @return
         */
        EntitiesOrHandlerPhase andPersister(
                @Nonnull ComponentFactory<SimpleRepositoryEntityPersister<ID, T>> persister
        );
    }

    /**
     *
     */
    interface EntitiesOrHandlerPhase {

        /**
         * @param idType
         * @param entityType
         * @param <ID>
         * @param <T>
         * @return
         */
        <ID, T> RepositoryPhase<ID, T> andEntity(@Nonnull Class<ID> idType,
                                                 @Nonnull Class<T> entityType);

        /**
         * @param commandName
         * @param commandHandler
         * @return
         */
        // TODO DISCUSS - have an explicit handlers() method to switch?
        HandlersOrEntityPhase withHandler(@Nonnull QualifiedName commandName,
                                          @Nonnull StatefulCommandHandler commandHandler);

        /**
         * @param commandName
         * @param commandHandlerBuilder
         * @return
         */
        HandlersOrEntityPhase withHandler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder
        );

        /**
         * @return
         */
        // TODO DISCUSS - how do I register components if I don't explicitly have a build point?
        StatefulCommandHandlingModule build();
    }

    /**
     *
     */
    interface HandlersOrEntityPhase {

        /**
         * @param commandName
         * @param commandHandler
         * @return
         */
        HandlersOrEntityPhase andHandler(@Nonnull QualifiedName commandName,
                                         @Nonnull StatefulCommandHandler commandHandler);

        /**
         * @param commandName
         * @param commandHandlerBuilder
         * @return
         */
        HandlersOrEntityPhase andHandler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder
        );

        /**
         * @param idType
         * @param entityType
         * @param <ID>
         * @param <T>
         * @return
         */
        // TODO DISCUSS - have an explicit entities() method to proceed with entities?
        <ID, T> RepositoryPhase<ID, T> withEntity(@Nonnull Class<ID> idType,
                                                  @Nonnull Class<T> entityType);

        /**
         * @return
         */
        // TODO DISCUSS - how do I register components if I don't explicitly have a build point?
        StatefulCommandHandlingModule build();
    }
}
