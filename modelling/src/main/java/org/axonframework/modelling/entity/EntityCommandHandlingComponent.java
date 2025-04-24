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

package org.axonframework.modelling.entity;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.Repository;

import java.util.Set;

public class EntityCommandHandlingComponent<ID, E> implements CommandHandlingComponent, DescribableComponent {

    private Repository<ID, E> repository;
    private EntityModel<E> entityModel;
    private EntityIdResolver<ID> idResolver;

    public EntityCommandHandlingComponent(Repository<ID, E> repository,
                                          EntityModel<E> entityModel,
                                          EntityIdResolver<ID> idResolver
    ) {
        this.repository = repository;
        this.entityModel = entityModel;
        this.idResolver = idResolver;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return entityModel.supportedCommands();
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                          @Nonnull ProcessingContext context) {
        try {
            ID id = idResolver.resolve(command, context);
            E entity = repository.load(id, context).join().entity();
            return entityModel.handle(command, entity, context);
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("repository", repository);
        descriptor.describeProperty("entityModel", entityModel);
        descriptor.describeProperty("idResolver", idResolver);
    }
}