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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandHandlerRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Interface describing a registry of {@link StatefulCommandHandler StatefulCommandHandlers} belonging to a single
 * component. During handling of commands, the stateful command handlers can access a model through a {@link ModelContainer}.
 * That container is filled based on the models registered to this registry through the {@link #registerModel(String, Class, Function, BiFunction)}
 *
 * @param <SELF> the type of the registry itself
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface StatefulCommandHandlerRegistry<SELF extends StatefulCommandHandlerRegistry<SELF>>
        extends CommandHandlerRegistry<SELF> {

    /**
     * Subscribe the given {@code commandHandler} for a {@link QualifiedName name}.
     *
     * @param name           The name of the given {@code commandHandler} can handle.
     * @param commandHandler The handler instance that handles {@link CommandMessage commands} for the given name.
     * @return This registry for fluent interfacing.
     */
    SELF subscribe(@Nonnull QualifiedName name, @Nonnull StatefulCommandHandler commandHandler);


    /**
     * Registers a model that can be resolved for registered {@link StatefulCommandHandler StatefulCommandHandlers}.
     * Will register this model under the simple name of the given {@code modelClass}. As each combination of a
     * {@code name} and {@code modelClass} can only be registered once, use {@link #registerModel(String, Class, Function, BiFunction)}
     * if you have need for multiple models of the same type to be registered.
     *
     * @param modelClass        The class of the model to register
     * @param commandIdResolver The function to resolve the identifier of the model based on a command. Returning
     *                          {@code null} from this function will result in an exception when trying to load this
     *                          model.
     * @param loadFunction      The function to load the model based on the identifier and the
     *                          {@link ProcessingContext}.
     * @param <ID>              The type of the identifier of the model
     * @param <T>               The type of the model
     * @return This registry for fluent interfacing
     */
    default <ID, T> SELF registerModel(
            Class<T> modelClass,
            Function<CommandMessage<?>, ID> commandIdResolver,
            BiFunction<ID, ProcessingContext, CompletableFuture<T>> loadFunction
    ) {
        return registerModel(modelClass.getSimpleName(), modelClass, commandIdResolver, loadFunction);
    }

    /**
     * Registers a model that can be resolved for registered {@link StatefulCommandHandler StatefulCommandHandlers}.
     * Each {@code name} and {@code modelClass} combination can only be registered once.
     *
     * @param name              The name of the model to register
     * @param modelClass        The class of the model to register
     * @param commandIdResolver The function to resolve the identifier of the model based on a command. Returning
     *                          {@code null} from this function will result in an exception when trying to load this
     *                          model.
     * @param loadFunction      The function to load the model based on the identifier and the
     *                          {@link ProcessingContext}.
     * @param <ID>              The type of the identifier of the model
     * @param <T>               The type of the model
     * @return This registry for fluent interfacing
     */
    <ID, T> SELF registerModel(String name,
                               Class<T> modelClass,
                               Function<CommandMessage<?>, ID> commandIdResolver,
                               BiFunction<ID, ProcessingContext, CompletableFuture<T>> loadFunction
    );
}
