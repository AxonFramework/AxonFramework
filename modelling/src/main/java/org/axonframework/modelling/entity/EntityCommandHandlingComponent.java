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
import org.axonframework.messaging.DelayedMessageStream;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.Repository;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A {@link CommandHandlingComponent} that handles commands for an entity. It will resolve the identifier of the entity
 * through the provided {@link EntityIdResolver}, load it from the provided {@link Repository} and delegate the handling
 * of the command to the {@link EntityModel} of the entity.
 *
 * @param <ID> The type of the identifier of the entity.
 * @param <E>  The type of the entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class EntityCommandHandlingComponent<ID, E> implements CommandHandlingComponent, DescribableComponent {

    private final Repository<ID, E> repository;
    private final EntityModel<E> entityModel;
    private final EntityIdResolver<ID> idResolver;
    private final Function<CommandMessage<?>, EntityCreationPolicy> creationPolicyProvider;

    /**
     * Creates a new {@link CommandHandlingComponent} that handles commands for the given entity type.
     *
     * @param repository     The {@link Repository} to load the entity from.
     * @param entityModel    The {@link EntityModel} to delegate the handling of the command to.
     * @param idResolver     The {@link EntityIdResolver} to resolve the identifier of the entity.
     * @param creationPolicyProvider The {@link EntityCreationPolicy} to use when creating the entity.
     */
    public EntityCommandHandlingComponent(
            @Nonnull Repository<ID, E> repository,
            @Nonnull EntityModel<E> entityModel,
            @Nonnull EntityIdResolver<ID> idResolver,
            @Nonnull Function<CommandMessage<?>, EntityCreationPolicy> creationPolicyProvider
    ) {
        this.repository = Objects.requireNonNull(repository, "The repository may not be null.");
        this.entityModel = Objects.requireNonNull(entityModel, "The entityModel may not be null.");
        this.idResolver = Objects.requireNonNull(idResolver, "The idResolver may not be null.");
        this.creationPolicyProvider = Objects.requireNonNull(creationPolicyProvider,
                                                             "The creationPolicyProvider may not be null.");

    }


    /**
     * Creates a new {@link CommandHandlingComponent} that handles commands for the given entity type, always creating
     * the entity if it does not exist.
     *
     * @param repository  The {@link Repository} to load the entity from.
     * @param entityModel The {@link EntityModel} to delegate the handling of the command to.
     * @param idResolver  The {@link EntityIdResolver} to resolve the identifier of the entity.
     */
    public EntityCommandHandlingComponent(
            @Nonnull Repository<ID, E> repository,
            @Nonnull EntityModel<E> entityModel,
            @Nonnull EntityIdResolver<ID> idResolver
    ) {
        this(repository, entityModel, idResolver, commandMessage -> EntityCreationPolicy.CREATE_IF_MISSING);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return entityModel.supportedCommands();
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                @Nonnull ProcessingContext context) {
        try {
            ID id = idResolver.resolve(command, context);
            EntityCreationPolicy creationPolicy = creationPolicyProvider.apply(command);
            if (creationPolicy == EntityCreationPolicy.ALWAYS) {
                return entityModel.handleCreate(command, context).first();
            }
            return DelayedMessageStream.createSingle(
                    repository.load(id, context)
                              .thenApply(me -> {
                                  if (me.entity() == null) {
                                      if (creationPolicy == EntityCreationPolicy.NEVER) {
                                          return MessageStream.failed(new IllegalStateException(
                                                  "No entity found for command " + command.type().qualifiedName()
                                                          + " with id " + id
                                          ));
                                      }
                                      return entityModel.handleCreate(command, context).first();
                                  }
                                  return entityModel.handleInstance(command, me.entity(), context).first();
                              }));
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("repository", repository);
        descriptor.describeProperty("entityModel", entityModel);
        descriptor.describeProperty("idResolver", idResolver);
        descriptor.describeProperty("creationPolicyProvider", creationPolicyProvider);
    }
}