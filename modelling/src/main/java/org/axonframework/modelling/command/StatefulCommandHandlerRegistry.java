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

import javax.annotation.Nonnull;

/**
 * Interface describing a registry of {@link StatefulCommandHandler stateful command handlers}.
 *
 * @param <S> The type of the registry itself.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface StatefulCommandHandlerRegistry<S extends StatefulCommandHandlerRegistry<S>>
        extends CommandHandlerRegistry<S> {

    /**
     * Subscribe the given {@link StatefulCommandHandler} for a {@link QualifiedName name}.
     *
     * @param name           The name of the given {@link StatefulCommandHandler} can handle.
     * @param commandHandler The handler instance that handles {@link CommandMessage commands} for the given name.
     * @return This registry for fluent interfacing.
     */
    S subscribe(@Nonnull QualifiedName name, @Nonnull StatefulCommandHandler commandHandler);
}
