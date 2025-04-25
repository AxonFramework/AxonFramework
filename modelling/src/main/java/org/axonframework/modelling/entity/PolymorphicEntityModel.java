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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
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
 * @param <E> the type of entity this model represents.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PolymorphicEntityModel<E> implements EntityModel<E>, DescribableComponent {

    private final EntityModel<E> superTypeModel;
    private final Map<Class<? extends E>, EntityModel<? extends E>> concreteModels;

    public PolymorphicEntityModel(
            EntityModel<E> abstractDelegate,
            List<EntityModel<? extends E>> concreteModels
    ) {
        this.superTypeModel = abstractDelegate;
        this.concreteModels = new HashMap<>();
        for (EntityModel<? extends E> polymorphicModel : concreteModels) {
            this.concreteModels.put(polymorphicModel.entityType(), polymorphicModel);
        }
    }

    public static <E> PolyMorphicEntityModelBuilder<E> forSuperType(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        superTypeModel.evolve(entity, event, context);
        return modelFor(entity).evolve(entity, event, context);
    }

    /**
     * Helper that “captures” the ? extends E for this particular entity instance.
     */
    @SuppressWarnings("unchecked")
    private <T extends E> EntityModel<T> modelFor(T entity) {
        // we know at runtime the model was stored under entity.getClass()
        return (EntityModel<T>) concreteModels.get(entity.getClass());
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        Set<QualifiedName> names = new HashSet<>(superTypeModel.supportedCommands());
        concreteModels.values().forEach(model -> names.addAll(model.supportedCommands()));
        return names;
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                          E entity,
                                                                          ProcessingContext context) {

        EntityModel<E> concreteModel = modelFor(entity);
        if (concreteModel.supportedCommands().contains(message.type().qualifiedName())) {
            return concreteModel.handle(message, entity, context);
        }
        return superTypeModel.handle(message, entity, context);
    }

    @Override
    public Class<E> entityType() {
        return superTypeModel.entityType();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType());
        descriptor.describeProperty("abstractCommandHandlingEntityModel", superTypeModel);
        descriptor.describeProperty("polymorphicModels", concreteModels);
    }

    /**
     * Builder for a {@link PolymorphicEntityModel}. This builder allows you to add concrete types to the model. Any
     * method inherited from {@link EntityModelBuilder} is delegated to the super type model.
     *
     * @param <E> The type of the entity this model represents.
     */
    public static class Builder<E> implements PolyMorphicEntityModelBuilder<E> {

        private final SimpleEntityModel.Builder<E> superTypeBuilder;
        private final List<EntityModel<? extends E>> polymorphicModels = new ArrayList<>();

        private Builder(Class<E> entityType) {
            this.superTypeBuilder = SimpleEntityModel.forEntityClass(entityType);
        }

        public Builder<E> commandHandler(QualifiedName qualifiedName,
                                         EntityCommandHandler<E> messageHandler) {
            superTypeBuilder.commandHandler(qualifiedName, messageHandler);
            return this;
        }

        @Override
        public Builder<E> addChild(EntityChildModel<?, E> child) {
            superTypeBuilder.addChild(child);
            return this;
        }


        public Builder<E> entityEvolver(EntityEvolver<E> entityEvolver) {
            superTypeBuilder.entityEvolver(entityEvolver);
            return this;
        }

        @Override
        public Builder<E> addConcreteType(EntityModel<? extends E> entityModel) {
            polymorphicModels.add(entityModel);
            return this;
        }

        public EntityModel<E> build() {
            EntityModel<E> superTypeModel = superTypeBuilder.build();
            return new PolymorphicEntityModel<>(superTypeModel, polymorphicModels);
        }
    }
}
