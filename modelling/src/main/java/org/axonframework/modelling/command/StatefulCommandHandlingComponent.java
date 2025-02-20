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
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * A {@link CommandHandlingComponent} implementation which allows for stateful handling of commands. This means it will
 * get a model to work with, which can be updated based on the command.
 *
 * @param <ID> The type of the identifier of the model
 * @param <T>  The type of the model this handler can handle
 */
public class StatefulCommandHandlingComponent<ID, T> implements
        CommandHandlingComponent,
        StatefulCommandHandlerRegistry<T, StatefulCommandHandlingComponent<ID, T>> {

    private final SimpleCommandHandlingComponent handlingComponent;
    private final AsyncRepository<ID, T> repository;
    private final Function<CommandMessage<?>, ID> idResolver;

    public StatefulCommandHandlingComponent(
            Class<T> modelClass,
            AsyncRepository<ID, T> repository,
            Function<CommandMessage<?>, ID> idResolver
    ) {
        this.handlingComponent = SimpleCommandHandlingComponent.forComponent(modelClass.getName());
        this.repository = repository;
        this.idResolver = idResolver;
    }


    @Override
    public StatefulCommandHandlingComponent<ID, T> subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulCommandHandler<T> commandHandler) {
        handlingComponent.subscribe(name, ((command, context) -> {
            try {
                ID id = idResolver.apply(command);
                ManagedEntity<ID, T> idtManagedEntity = repository.loadOrCreate(id, context).get();
                return commandHandler.handle(command, idtManagedEntity.entity(), context);
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        }));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        return handlingComponent.handle(command, context);
    }

    @Override
    public StatefulCommandHandlingComponent<ID, T> subscribe(@Nonnull QualifiedName name,
                                                             @Nonnull CommandHandler commandHandler) {
        handlingComponent.subscribe(name, commandHandler);
        return this;
    }

    @Override
    public StatefulCommandHandlingComponent<ID, T> self() {
        return this;
    }


    @Override
    public Set<QualifiedName> supportedCommands() {
        return handlingComponent.supportedCommands();
    }
}
