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
import org.axonframework.modelling.entity.child.EvolvableEntityChildModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@SuppressWarnings("MissingJavadoc")
public class SimpleEvolvableEntityModel<E> implements EvolvableEntityModel<E>, DescribableComponent {

    private final EntityModel<E> commandHandlingEntityModel;
    private final Map<Class<?>, EvolvableEntityChildModel<?, E>> children = new HashMap<>();
    private final EntityEvolver<E> entityEvolver;


    private SimpleEvolvableEntityModel(EntityModel<E> delegate,
                                       EntityEvolver<E> entityEvolver,
                                       List<EntityChildModel<?, E>> children) {
        this.commandHandlingEntityModel = delegate;
        this.entityEvolver = entityEvolver;
        for (EntityChildModel<?, E> child : children) {
            if (child instanceof EvolvableEntityChildModel<?, ?>) {
                this.children.put(child.entityType(), (EvolvableEntityChildModel<?, E>) child);
            }
        }
    }

    public static <E> Builder<E> forEntityClass(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return commandHandlingEntityModel.supportedCommands();
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message, E entity,
                                                                          ProcessingContext context) {
        return commandHandlingEntityModel.handle(message, entity, context);
    }

    @Override
    public Class<E> entityType() {
        return commandHandlingEntityModel.entityType();
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        var currentEntity = entity;
        for (EvolvableEntityChildModel<?, E> child : children.values()) {
            currentEntity = child.evolve(currentEntity, event, context);
        }
        return entityEvolver.evolve(currentEntity, event, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType());
        descriptor.describeProperty("entityEvolver", entityEvolver);
        descriptor.describeProperty("children", children);
        descriptor.describeWrapperOf(commandHandlingEntityModel);
    }


    public static class Builder<E> implements EvolvableEntityModelBuilder<E> {

        private final List<EntityChildModel<?, E>> children = new ArrayList<>();
        private final SimpleEntityModel.Builder<E> delegateBuilder;
        private EntityEvolver<E> entityEvolver;

        public Builder(Class<E> entityType) {
            this.delegateBuilder = SimpleEntityModel.forEntityClass(entityType);
        }

        @Override
        public Builder<E> commandHandler(QualifiedName qualifiedName,
                                         EntityCommandHandler<E> messageHandler) {
            delegateBuilder.commandHandler(qualifiedName, messageHandler);
            return this;
        }

        @Override
        public Builder<E> addChild(EntityChildModel<?, E> child) {
            if (child instanceof EvolvableEntityChildModel<?, E>) {
                children.add(child);
            }
            delegateBuilder.addChild(child);
            return this;
        }

        public Builder<E> entityEvolver(EntityEvolver<E> entityEvolver) {
            this.entityEvolver = entityEvolver;
            return this;
        }

        @Override
        public EvolvableEntityModel<E> build() {
            EntityModel<E> delegate = delegateBuilder.build();
            return new SimpleEvolvableEntityModel<>(delegate, entityEvolver, children);
        }
    }
}
