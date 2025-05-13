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
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Polymorphic {@link EntityModel} that represents an entity that can have multiple concrete types. For example,
 * {@code Employee} and {@code Customer} could be two concrete types of {@code Person}, sharing properties and a set of
 * commands and events.
 * <p>
 * This model delegates commands to the concrete type if the concrete type is registered for the command. If not, it
 * will attempt to handle the command with the super type. Concrete types thus take precedence over the super type for
 * commands.
 * <p>
 * Events are delegates to both the super type and the concrete type.
 *
 * @param <E> The type of polymorphic entity this model represents.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PolymorphicEntityModel<E> implements EntityModel<E>, DescribableComponent {

    private final EntityModel<E> superTypeModel;
    private final Map<Class<? extends E>, EntityModel<? extends E>> concreteModels;
    private final Set<QualifiedName> supportedCommandNames = new HashSet<>();
    private final Set<QualifiedName> supportedInstanceCommandNames = new HashSet<>();
    private final Set<QualifiedName> supportedCreationalCommandNames = new HashSet<>();

    private PolymorphicEntityModel(
            EntityModel<E> superTypeModel,
            List<EntityModel<? extends E>> concreteModels
    ) {
        this.superTypeModel = Objects.requireNonNull(superTypeModel, "The superTypeModel may not be null.");
        Objects.requireNonNull(concreteModels, "The concreteModels may not be null.");
        this.concreteModels = new HashMap<>();
        this.supportedCommandNames.addAll(superTypeModel.supportedCommands());
        this.supportedInstanceCommandNames.addAll(this.superTypeModel.supportedInstanceCommands());
        this.supportedCreationalCommandNames.addAll(superTypeModel.supportedCreationalCommands());
        for (EntityModel<? extends E> polymorphicModel : concreteModels) {
            this.concreteModels.put(polymorphicModel.entityType(), polymorphicModel);
            this.supportedCommandNames.addAll(polymorphicModel.supportedCommands());
            this.supportedInstanceCommandNames.addAll(polymorphicModel.supportedInstanceCommands());
            this.supportedCreationalCommandNames.addAll(polymorphicModel.supportedCreationalCommands());
        }
    }

    /**
     * Creates a new polymorphic {@link EntityModel} for the given super type. The model can then be used to add
     * concrete types to the model. Any method inherited from {@link EntityModelBuilder} is delegated to the super type
     * model.
     *
     * @param entityType The type of the entity to create a model for.
     * @param <E>        The type of the entity to create a model for.
     * @return A new {@link Builder} for the given entity type.
     */
    public static <E> PolymorphicEntityModelBuilder<E> forSuperType(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        var superTypeEvolvedEntity = superTypeModel.evolve(entity, event, context);
        return modelFor(entity).evolve(superTypeEvolvedEntity, event, context);
    }

    /**
     * Helper that “captures” the ? extends E for this particular entity instance.
     */
    @SuppressWarnings("unchecked")
    private <T extends E> EntityModel<T> modelFor(T entity) {
        // we know at runtime the model was stored under entity.getClass()
        return (EntityModel<T>) concreteModels.get(entity.getClass());
    }

    @Nonnull
    @Override
    public Set<QualifiedName> supportedCommands() {
        return supportedCommandNames;
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedCreationalCommands() {
        return supportedCreationalCommandNames;
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedInstanceCommands() {
        return supportedInstanceCommandNames;
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage<?>> handleCreate(CommandMessage<?> message,
                                                                      ProcessingContext context) {
        if (isInstanceCommand(message) && !isCreationalCommand(message)) {
            return MessageStream.failed(new EntityMissingForInstanceCommandHandler(message));
        }
        for(EntityModel<? extends E> concreteModel : concreteModels.values()) {
            if (concreteModel.supportedCreationalCommands().contains(message.type().qualifiedName())) {
                return concreteModel.handleCreate(message, context);
            }
        }
        if(superTypeModel.supportedCreationalCommands().contains(message.type().qualifiedName())) {
            return superTypeModel.handleCreate(message, context);
        }
        return MessageStream.failed(new NoHandlerForCommandException(message, entityType()));
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage<?>> handleInstance(@Nonnull CommandMessage<?> message,
                                                                        @Nonnull E entity,
                                                                        @Nonnull ProcessingContext context) {
        if (isCreationalCommand(message) && !isInstanceCommand(message)) {
            return MessageStream.failed(new EntityExistsForCreationalCommandHandler(message, entity));
        }
        EntityModel<E> concreteModel = modelFor(entity);
        if (concreteModel.supportedInstanceCommands().contains(message.type().qualifiedName())) {
            return concreteModel.handleInstance(message, entity, context);
        }
        if(superTypeModel.supportedInstanceCommands().contains(message.type().qualifiedName())) {
            return superTypeModel.handleInstance(message, entity, context);
        }

        return MessageStream.failed(new NoHandlerForCommandException(message, entityType()));
    }

    @Nonnull
    @Override
    public Class<E> entityType() {
        return superTypeModel.entityType();
    }

    private boolean isCreationalCommand(CommandMessage<?> message) {
        return supportedCreationalCommandNames.contains(message.type().qualifiedName());
    }

    private boolean isInstanceCommand(CommandMessage<?> message) {
        return supportedInstanceCommandNames.contains(message.type().qualifiedName());
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType());
        descriptor.describeProperty("superTypeModel", superTypeModel);
        descriptor.describeProperty("polymorphicModels", concreteModels);
    }

    @Override
    public String toString() {
        return "PolymorphicEntityModel{entityType=" + entityType().getName() + '}';
    }

    /**
     * Builder for a {@link PolymorphicEntityModel}. This builder allows you to add concrete types to the model. Any
     * method inherited from {@link EntityModelBuilder} is delegated to the super type model.
     *
     * @param <E> The type of the entity this model represents.
     */
    private static class Builder<E> implements PolymorphicEntityModelBuilder<E> {

        private final EntityModelBuilder<E> superTypeBuilder;
        private final List<EntityModel<? extends E>> polymorphicModels = new ArrayList<>();

        private Builder(Class<E> entityType) {
            this.superTypeBuilder = SimpleEntityModel.forEntityClass(entityType);
        }

        @Nonnull
        @Override
        public Builder<E> instanceCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                 @Nonnull EntityCommandHandler<E> messageHandler) {
            superTypeBuilder.instanceCommandHandler(qualifiedName, messageHandler);
            return this;
        }

        @Nonnull
        @Override
        public EntityModelBuilder<E> creationalCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                              @Nonnull CommandHandler messageHandler) {
            superTypeBuilder.creationalCommandHandler(qualifiedName, messageHandler);
            return this;
        }

        @Nonnull
        @Override
        public Builder<E> addChild(@Nonnull EntityChildModel<?, E> child) {
            superTypeBuilder.addChild(child);
            return this;
        }


        @Nonnull
        @Override
        public Builder<E> entityEvolver(@Nullable EntityEvolver<E> entityEvolver) {
            superTypeBuilder.entityEvolver(entityEvolver);
            return this;
        }

        @Override
        @Nonnull
        public Builder<E> addConcreteType(@Nonnull EntityModel<? extends E> entityModel) {
            Objects.requireNonNull(entityModel, "The entityModel may not be null.");
            if (polymorphicModels.stream().anyMatch(p -> p.entityType().equals(entityModel.entityType()))) {
                throw new IllegalArgumentException("Concrete type [%s] already registered for this model.".formatted(
                        entityModel.entityType().getName()));
            }
            polymorphicModels.add(entityModel);
            return this;
        }

        @Override
        @Nonnull
        public EntityModel<E> build() {
            EntityModel<E> superTypeModel = superTypeBuilder.build();
            return new PolymorphicEntityModel<>(superTypeModel, polymorphicModels);
        }
    }
}
