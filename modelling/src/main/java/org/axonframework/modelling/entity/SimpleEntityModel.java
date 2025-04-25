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
 *
 */
@SuppressWarnings("MissingJavadoc")
public class SimpleEntityModel<E> implements DescribableComponent, EntityModel<E> {

    private final Class<E> entityType;
    private final Map<Class<?>, EntityChildModel<?, E>> children = new HashMap<>();
    private final Map<QualifiedName, EntityCommandHandler<E>> commandHandlers = new HashMap<>();
    private final EntityEvolver<E> entityEvolver;

    private SimpleEntityModel(Class<E> entityType,
                              Map<QualifiedName, EntityCommandHandler<E>> commandHandlers,
                              List<EntityChildModel<?, E>> children, EntityEvolver<E> entityEvolver) {
        this.entityType = entityType;
        this.entityEvolver = entityEvolver;
        this.commandHandlers.putAll(commandHandlers);
        children.forEach(child -> this.children.put(child.entityType(), child));
    }

    public static <E> Builder<E> forEntityClass(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        Set<QualifiedName> qualifiedNames = new HashSet<>(commandHandlers.keySet());
        children.values().forEach(child -> qualifiedNames.addAll(child.supportedCommands()));
        return qualifiedNames;
    }


    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        var currentEntity = entity;
        for (EntityChildModel<?, E> child : children.values()) {
            currentEntity = child.evolve(currentEntity, event, context);
        }
        return entityEvolver.evolve(currentEntity, event, context);
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message, E entity,
                                                                          ProcessingContext context) {
        try {
            // First try to find child entity able to handle
            for (Map.Entry<Class<?>, EntityChildModel<?, E>> entry : children.entrySet()) {
                EntityChildModel<?, E> child = entry.getValue();
                if (child.supportedCommands().contains(message.type().qualifiedName())) {
                    return child.handle(message, entity, context);
                }
            }

            // If no child entity is able to handle, try to find command handler
            EntityCommandHandler<E> commandHandler = commandHandlers.get(message.type().qualifiedName());
            if (commandHandler != null) {
                return commandHandler.handle(message, entity, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
        throw new IllegalArgumentException(
                "No command handler found for command " + message.type().qualifiedName() + " on entity " + entity
        );
    }

    @Override
    public Class<E> entityType() {
        return entityType;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("commandHandlers", commandHandlers);
        descriptor.describeProperty("children", children);
    }


    public static class Builder<E> implements EntityModelBuilder<E> {

        private final Class<E> entityType;
        private final Map<QualifiedName, EntityCommandHandler<E>> commandHandlers = new HashMap<>();
        private final List<EntityChildModel<?, E>> children = new ArrayList<>();
        private EntityEvolver<E> entityEvolver;

        public Builder(Class<E> entityType) {
            this.entityType = entityType;
        }

        public Builder<E> commandHandler(QualifiedName qualifiedName,
                                                    EntityCommandHandler<E> messageHandler) {
            if (commandHandlers.containsKey(qualifiedName)) {
                throw new IllegalArgumentException(
                        "Command handler with name " + qualifiedName + " already registered");
            }
            commandHandlers.put(qualifiedName, messageHandler);
            return this;
        }

        public Builder<E> addChild(EntityChildModel<?, E> child) {
            children.add(child);
            return this;
        }

        @Override
        public EntityModelBuilder<E> entityEvolver(EntityEvolver<E> entityEvolver) {
            this.entityEvolver = entityEvolver;
            return this;
        }

        public EntityModel<E> build() {
            return new SimpleEntityModel<>(entityType, commandHandlers, children, entityEvolver);
        }
    }
}
