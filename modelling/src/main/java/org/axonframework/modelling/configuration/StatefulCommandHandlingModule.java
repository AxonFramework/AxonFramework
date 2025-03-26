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

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link Module} implementation providing operation to construct a stateful command handling application module.
 * <p>
 * The {@code StatefulCommandHandlingModule} follows a builder paradigm, wherein several entities and
 * {@link StatefulCommandHandler StatefulCommandHandlers} can be registered in either order. To initiate entity
 * registration, you should move into the entity registration phase by invoking {@link SetupPhase#entities()}. To
 * register command handlers a similar registration phase switch should be made, by invoking
 * {@link SetupPhase#commandHandlers()}.
 * <p>
 * When {@link EntityPhase#entity(Class, Class) registering entities}, the entity's type and identifier type are
 * expected first. From there, either a {@link AsyncRepository} or a separate {@link SimpleRepositoryEntityLoader} and
 * {@link SimpleRepositoryEntityPersister} are expected. When all entities have been registered, you can switch to
 * {@link SetupPhase#commandHandlers()} to start registering command handlers}.
 * <p>
 * To register stateful command handlers, the {@link CommandHandlerPhase#handler(QualifiedName, StatefulCommandHandler)}
 * is used. If the construction of the {@code StatefulCommandHandler} requires components from the
 * {@link org.axonframework.configuration.NewConfiguration}, the
 * {@link CommandHandlerPhase#handler(QualifiedName, ComponentFactory)} operation should be used instead.
 * <p>
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
    static SetupPhase module(String moduleName) {
        return new StatefulCommandHandlingModuleImpl(moduleName);
    }

    /**
     *
     */
    interface SetupPhase {

        CommandHandlerPhase commandHandlers();

        default CommandHandlerPhase commandHandlers(Consumer<CommandHandlerPhase> phaseConsumer) {
            CommandHandlerPhase commandHandlerPhase = commandHandlers();
            phaseConsumer.accept(commandHandlerPhase);
            return commandHandlerPhase;
        }

        EntityPhase entities();

        default EntityPhase entities(Consumer<EntityPhase> phaseConsumer) {
            EntityPhase entityPhase = entities();
            phaseConsumer.accept(entityPhase);
            return entityPhase;
        }

        // TODO DISCUSS - Do we want a withStateManager method? This should replace the entity flow entirely I think.
    }

    interface CommandHandlerPhase extends SetupPhase, BuildPhase {

        /**
         * @param commandName
         * @param commandHandler
         * @return
         */
        default CommandHandlerPhase handler(@Nonnull QualifiedName commandName,
                                            @Nonnull StatefulCommandHandler commandHandler) {
            Objects.requireNonNull(commandHandler, "The stateful command Handler cannot be null.");
            return handler(commandName, c -> commandHandler);
        }

        /**
         * @param commandName
         * @param commandHandlerBuilder
         * @return
         */
        CommandHandlerPhase handler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder
        );
    }

    interface EntityPhase extends SetupPhase, BuildPhase {

        /**
         * @param idType
         * @param entityType
         * @param <I>
         * @param <E>
         * @return
         */
        <I, E> RepositoryPhase<I, E> entity(@Nonnull Class<I> idType,
                                            @Nonnull Class<E> entityType);
    }

    /**
     * @param <I>
     * @param <E>
     */
    interface RepositoryPhase<I, E> {

        /**
         * @param loader
         * @return
         */
        PersisterPhase<I, E> loader(@Nonnull ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loader);

        /**
         * @param repository
         * @return
         */
        EntityPhase repository(@Nonnull ComponentFactory<AsyncRepository<I, E>> repository);
    }

    /**
     * @param <I>
     * @param <E>
     */
    interface PersisterPhase<I, E> {

        /**
         * @param persister
         * @return
         */
        EntityPhase persister(@Nonnull ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persister);
    }

    /**
     *
     */
    interface BuildPhase {

        /**
         * @return
         */
        // TODO DISCUSS - how do I register components if I don't explicitly have a build point?
        StatefulCommandHandlingModule build();
    }
}
