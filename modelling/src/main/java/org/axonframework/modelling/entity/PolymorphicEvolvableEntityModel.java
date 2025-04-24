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

public class PolymorphicEvolvableEntityModel<E> implements EvolvableEntityModel<E>, DescribableComponent {

    private final EvolvableEntityModel<E> abstractEntityModel;
    private final Map<Class<? extends E>, EvolvableEntityModel<? extends E>> polymorphicModels;

    public PolymorphicEvolvableEntityModel(EvolvableEntityModel<E> abstractDelegate,
                                           List<EvolvableEntityModel<? extends E>> polymorphicModels) {
        this.abstractEntityModel = abstractDelegate;
        this.polymorphicModels = new HashMap<>();
        for (EvolvableEntityModel<? extends E> polymorphicModel : polymorphicModels) {
            this.polymorphicModels.put(polymorphicModel.entityType(), polymorphicModel);
        }
    }

    public static <E> Builder<E> forAbstractEntityClass(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        abstractEntityModel.evolve(entity, event, context);
        return modelFor(entity).evolve(entity, event, context);
    }

    /**
     * Helper that “captures” the ? extends E for this particular entity instance.
     */
    @SuppressWarnings("unchecked")
    private <T extends E> EvolvableEntityModel<T> modelFor(T entity) {
        // we know at runtime the model was stored under entity.getClass()
        return (EvolvableEntityModel<T>) polymorphicModels.get(entity.getClass());
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        Set<QualifiedName> names = new HashSet<>(abstractEntityModel.supportedCommands());
        polymorphicModels.values().forEach(model -> names.addAll(model.supportedCommands()));
        return names;
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                          E entity,
                                                                          ProcessingContext context) {

        EvolvableEntityModel<E> eEvolvableEntityModel = modelFor(entity);
        if (eEvolvableEntityModel.supportedCommands().contains(message.type().qualifiedName())) {
            return eEvolvableEntityModel.handle(message, entity, context);
        }
        return abstractEntityModel.handle(message, entity, context);
    }

    @Override
    public Class<E> entityType() {
        return abstractEntityModel.entityType();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType());
        descriptor.describeProperty("abstractCommandHandlingEntityModel", abstractEntityModel);
        descriptor.describeProperty("polymorphicModels", polymorphicModels);
    }


    public static class Builder<E> implements
            EvolvableEntityModelBuilder<E>
    {

        private final SimpleEvolvableEntityModel.Builder<E> abstractDelegate;
        private final List<EvolvableEntityModel<? extends E>> polymorphicModels = new ArrayList<>();

        public Builder(Class<E> entityType) {
            this.abstractDelegate = SimpleEvolvableEntityModel.forEntityClass(entityType);
        }

        public Builder<E> commandHandler(QualifiedName qualifiedName,
                                         EntityCommandHandler<E> messageHandler) {
            abstractDelegate.commandHandler(qualifiedName, messageHandler);
            return this;
        }

        @Override
        public Builder<E> addChild(EntityChildModel<?, E> child) {
            abstractDelegate.addChild(child);
            return this;
        }


        public Builder<E> entityEvolver(EntityEvolver<E> entityEvolver) {
            abstractDelegate.entityEvolver(entityEvolver);
            return this;
        }

        public Builder<E> addPolymorphicModel(EvolvableEntityModel<? extends E> polymorphicModel) {
            polymorphicModels.add(polymorphicModel);
            return this;
        }

        public PolymorphicEvolvableEntityModel<E> build() {
            EvolvableEntityModel<E> abstractModel = abstractDelegate.build();
            return new PolymorphicEvolvableEntityModel<>(abstractModel, polymorphicModels);
        }
    }
}
