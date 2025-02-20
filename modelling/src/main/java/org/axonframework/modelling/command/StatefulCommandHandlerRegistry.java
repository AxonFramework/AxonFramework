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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.QualifiedName;

import javax.annotation.Nonnull;

/**
 * Interface describing a registry of {@link CommandHandler command handlers}.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 * @param <T> The type of entity this registry will handle
 * @param <SELF> the type of the registry itself
 */
public interface StatefulCommandHandlerRegistry<T, SELF extends StatefulCommandHandlerRegistry<T, SELF>>
        extends CommandHandlerRegistry<SELF> {

    /**
     * Subscribe the given {@code commandHandler} for a {@link QualifiedName name}.
     *
     * @param name           The name of the given {@code commandHandler} can handle.
     * @param commandHandler The handler instance that handles {@link CommandMessage commands} for the given name.
     * @return This registry for fluent interfacing.
     */
    SELF subscribe(@Nonnull QualifiedName name, @Nonnull StatefulCommandHandler<T> commandHandler);

    /**
     * Subscribe the given {@code commandHandler} for a {@link QualifiedName name}.
     * @return This registry for fluent interfacing.
     */
    SELF self();
}
