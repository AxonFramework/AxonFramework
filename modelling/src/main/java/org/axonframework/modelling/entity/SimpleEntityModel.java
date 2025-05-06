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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link EntityModel} interface that enables the definition of command handlers and child
 * entities for a given entity type {@code E}. Optionally, an {@link EntityEvolver} can be provided to evolve the entity
 * state based on events. If no {@link EntityEvolver} is provided, state can exclusively be changed through command
 * handlers.
 * <p>
 * During the handling of commands, handlers defined in child entities take precedence over the parent entity's command
 * handlers. Event handlers are invoked on both the parent and child models, with child models being invoked first.
 *
 * @param <E> The type of the entity this model describes.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SimpleEntityModel<E> implements DescribableComponent, EntityModel<E> {

    private final Class<E> entityType;
    private final Map<Class<?>, EntityChildModel<?, E>> children = new HashMap<>();
    private final Map<QualifiedName, EntityCommandHandler<E>> commandHandlers = new HashMap<>();
    private final EntityEvolver<E> entityEvolver;
    private final Set<QualifiedName> supportedCommandNames = new HashSet<>();

    private SimpleEntityModel(@Nonnull Class<E> entityType,
                              @Nonnull Map<QualifiedName, EntityCommandHandler<E>> commandHandlers,
                              @Nonnull List<EntityChildModel<?, E>> children,
                              @Nullable EntityEvolver<E> entityEvolver) {
        this.entityType = Objects.requireNonNull(entityType, "entityType may not be null");
        this.entityEvolver = entityEvolver;
        this.commandHandlers.putAll(Objects.requireNonNull(commandHandlers, "commandHandlers may not be null"));
        Objects.requireNonNull(children, "children may not be null")
               .forEach(child -> this.children.put(child.entityType(), child));

        // To prevent constantly creating new sets, we create a single set and add all command handlers and children to it.
        supportedCommandNames.addAll(commandHandlers.keySet());
        children.forEach(child -> supportedCommandNames.addAll(child.supportedCommands()));
    }

    /**
     * Creates a {@link Builder builder} for the specified entity type. This builder provides a fluent API for defining
     * and constructing an {@link EntityModel} for the given entity class, allowing the registration of command
     * handlers, child entities, and an optional entity evolver.
     *
     * @param <E>        The type of the entity for which the model is being built.
     * @param entityType The {@code Class} object representing the entity type.
     * @return A {@link Builder} instance configured for the specified entity type.
     */
    @Nonnull
    public static <E> Builder<E> forEntityClass(@Nonnull Class<E> entityType) {
        Objects.requireNonNull(entityType, "entityType may not be null");
        return new Builder<>(entityType);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return supportedCommandNames;
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        var currentEntity = entity;
        for (EntityChildModel<?, E> child : children.values()) {
            currentEntity = child.evolve(currentEntity, event, context);
        }
        if (entityEvolver == null) {
            return currentEntity;
        }
        return entityEvolver.evolve(currentEntity, event, context);
    }

    @Override
    public MessageStream.Single<CommandResultMessage<?>> handle(CommandMessage<?> message, E entity,
                                                                ProcessingContext context) {
        try {
            var childrenWithCommandHandlers = children.values().stream()
                                                      .filter(childEntity -> childEntity
                                                              .supportedCommands()
                                                              .contains(message.type().qualifiedName()))
                                                      .collect(Collectors.toList());
            if (!childrenWithCommandHandlers.isEmpty()) {
                return handleForChildren(childrenWithCommandHandlers, message, entity, context);
            }

            EntityCommandHandler<E> commandHandler = commandHandlers.get(message.type().qualifiedName());
            if (commandHandler != null) {
                return commandHandler.handle(message, entity, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }

        return MessageStream.failed(new MissingCommandHandlerException(message, entityType));
    }

    private MessageStream.Single<CommandResultMessage<?>> handleForChildren(
            List<EntityChildModel<?, E>> childrenWithCommandHandler,
            CommandMessage<?> message,
            E entity,
            ProcessingContext context) {
        if (childrenWithCommandHandler.size() == 1) {
            return childrenWithCommandHandler.getFirst().handle(message, entity, context);
        }

        // There are multiple children that can handle the command. We need to find the ONE that can handle it.
        var matchingChildren = childrenWithCommandHandler
                .stream()
                .filter(childEntity -> childEntity.canHandle(message, entity, context))
                .toList();
        if (matchingChildren.size() == 1) {
            return matchingChildren.getFirst().handle(message, entity, context);
        }
        if (matchingChildren.size() > 1) {
            return MessageStream.failed(new ChildAmbiguityException(message, entity));
        }
        return MessageStream.failed(new ChildEntityMissingException(message, entity));
    }

    @Override
    public Class<E> entityType() {
        return entityType;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("commandHandlers", commandHandlers);
        descriptor.describeProperty("supportedCommandNames", supportedCommandNames);
        descriptor.describeProperty("entityEvolver", entityEvolver);
        descriptor.describeProperty("children", children);
    }

    @Override
    public String toString() {
        return "SimpleEntityModel{entityType=" + entityType.getName() + '}';
    }

    /**
     * Builder class for constructing an {@link EntityModel} for a specific entity type. This class provides a fluent
     * API to configure the entity model by specifying command handlers, child entities, and the entity evolver.
     *
     * @param <E> The type of the entity for which the model is being constructed.
     */
    public static class Builder<E> implements EntityModelBuilder<E> {

        private final Class<E> entityType;
        private final Map<QualifiedName, EntityCommandHandler<E>> commandHandlers = new HashMap<>();
        private final List<EntityChildModel<?, E>> children = new ArrayList<>();
        private EntityEvolver<E> entityEvolver;

        private Builder(Class<E> entityType) {
            this.entityType = entityType;
        }

        @Nonnull
        @Override
        public Builder<E> commandHandler(@Nonnull QualifiedName qualifiedName,
                                         @Nonnull EntityCommandHandler<E> messageHandler) {
            Objects.requireNonNull(qualifiedName, "qualifiedName may not be null");
            Objects.requireNonNull(messageHandler, "messageHandler may not be null");
            if (commandHandlers.containsKey(qualifiedName)) {
                throw new IllegalArgumentException(
                        "Command handler with name " + qualifiedName + " already registered");
            }
            commandHandlers.put(qualifiedName, messageHandler);
            return this;
        }

        @Nonnull
        @Override
        public Builder<E> addChild(@Nonnull EntityChildModel<?, E> child) {
            Objects.requireNonNull(child, "child may not be null");
            children.add(child);
            return this;
        }

        @Nonnull
        @Override
        public EntityModelBuilder<E> entityEvolver(@Nullable EntityEvolver<E> entityEvolver) {
            this.entityEvolver = entityEvolver;
            return this;
        }

        @Nonnull
        public EntityModel<E> build() {
            return new SimpleEntityModel<>(entityType, commandHandlers, children, entityEvolver);
        }
    }
}
