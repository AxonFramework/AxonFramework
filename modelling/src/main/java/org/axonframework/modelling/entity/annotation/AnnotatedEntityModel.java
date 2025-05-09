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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.annotation.CommandMessageHandlingMember;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.EntityModelBuilder;
import org.axonframework.modelling.entity.PolymorphicEntityModel;
import org.axonframework.modelling.entity.PolymorphicEntityModelBuilder;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;
import static org.axonframework.messaging.annotation.AnnotatedHandlerInspector.inspectType;

/**
 * An {@link EntityModel} implementation that uses reflection to inspect the entity. It will detect command- and
 * event-handling methods, as well as child entities. These will then be registered to a delegate {@link EntityModel},
 * so that essentially a declared model is built from annotations of which it's structure is clearly defined.
 * <p>
 * An annotation on the class to indicate it as an entity is not required. The {@link EntityModel} is created based on
 * the methods and fields of the class. Methods annotated with {@link CommandHandler} will be registered as command
 * handlers. Methods annotated with {@link org.axonframework.eventhandling.annotation.EventHandler} will be registered
 * as {@link org.axonframework.modelling.EntityEvolver}. And fields or getters annotated with {@link EntityMember} will
 * be registered as child entities.
 * <p>
 * NOTE: This class is a complete rewrite of the pre-5.0.0 {@code AnnotatedAggregateMetaModelFactory}. Both scan the
 * class for annotated methods and fields, but the AnnotatedEntityModel dropped aggregate versioning (conflict
 * resolution), no longer required an id in the entity, and creates a declarative model instead of relying on reflection
 * at runtime.
 *
 * @param <E> The type of entity this model describes.
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @since 5.0.0
 */
public class AnnotatedEntityModel<E> implements EntityModel<E>, DescribableComponent {

    private final Class<E> entityType;
    private final EntityModel<E> entityModel;
    private final ParameterResolverFactory parameterResolverFactory;
    private final MessageTypeResolver messageTypeResolver;
    private final Map<QualifiedName, Class<?>> payloadTypes = new HashMap<>();
    private final List<AnnotatedEntityModel<?>> children = new LinkedList<>();

    /**
     * Instantiate an annotated {@link EntityModel} of a concrete (i.e. non-polymorphic) entity type.
     *
     * @param entityType               The concrete entity type this model describes.
     * @param parameterResolverFactory The {@link ParameterResolverFactory} to use for resolving parameters.
     * @param messageTypeResolver      The {@link MessageTypeResolver} to use for resolving message types from payload
     *                                 classes.
     */
    public AnnotatedEntityModel(
            @Nonnull Class<E> entityType,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        this(entityType, parameterResolverFactory, messageTypeResolver, Set.of());
    }


    /**
     * Instantiate an annotated {@link EntityModel} of an entity type. If the supplied {@code concreteTypes} is not
     * empty, the entity type is considered polymorphic. The concrete types are used to initialize the model.
     *
     * @param entityType               The concrete entity type this model describes.
     * @param parameterResolverFactory The {@link ParameterResolverFactory} to use for resolving parameters.
     * @param messageTypeResolver      The {@link MessageTypeResolver} to use for resolving message types from payload
     *                                 classes.
     * @param concreteTypes            The concrete types of the polymorphic entity type.
     */
    public AnnotatedEntityModel(
            @Nonnull Class<E> entityType,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver,
            @Nonnull Set<Class<? extends E>> concreteTypes
    ) {
        this.entityType = requireNonNull(entityType, "The entityType may not be null.");
        this.parameterResolverFactory = requireNonNull(parameterResolverFactory,
                                                       "The parameterResolverFactory may not be null.");
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The messageTypeResolver may not be null.");
        requireNonNull(concreteTypes, "The concreteTypes may not be null.");
        if (!concreteTypes.isEmpty()) {
            this.entityModel = initializePolymorphicModel(entityType, concreteTypes);
        } else {
            this.entityModel = initializeConcreteModel(entityType);
        }
    }

    private EntityModel<E> initializeConcreteModel(Class<E> entityType) {
        EntityModelBuilder<E> builder = EntityModel.forEntityType(entityType);
        return initializeEntityModel(builder, entityType);
    }

    private EntityModel<E> initializePolymorphicModel(Class<E> entityType, Set<Class<? extends E>> concreteTypes) {
        PolymorphicEntityModelBuilder<E> builder = PolymorphicEntityModel.forSuperType(entityType);
        concreteTypes.forEach(concreteType -> builder.addConcreteType(new AnnotatedEntityModel<>(
                concreteType, parameterResolverFactory, messageTypeResolver
        )));
        return initializeEntityModel(builder, entityType);
    }

    private EntityModel<E> initializeEntityModel(EntityModelBuilder<E> builder, Class<E> entityType) {
        AnnotatedHandlerInspector<E> inspected = inspectType(entityType, parameterResolverFactory);
        builder.entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(entityType));
        initializeCommandHandlers(builder, inspected);
        initializeChildren(builder);
        return builder.build();
    }

    private void initializeCommandHandlers(EntityModelBuilder<E> builder, AnnotatedHandlerInspector<E> inspected) {
        inspected.getHandlers(entityType)
                 .forEach(handler -> {
                     QualifiedName qualifiedName = messageTypeResolver.resolve(handler.payloadType()).qualifiedName();
                     if (handler instanceof CommandMessageHandlingMember<? super E> cmhm) {
                         if (cmhm.isFactoryHandler()) {
                             builder.creationalCommandHandler(qualifiedName, ((command, context) -> handler
                                     .handle(command, context, null)
                                     .<CommandResultMessage<?>>mapMessage(
                                             GenericCommandResultMessage::new)
                                     .first()));
                             return;
                         }
                         builder.commandHandler(qualifiedName, ((command, entity, context) -> handler
                                 .handle(command, context, entity)
                                 .<CommandResultMessage<?>>mapMessage(GenericCommandResultMessage::new)
                                 .first()));
                     }
                     payloadTypes.put(qualifiedName, handler.payloadType());
                 });
    }

    /**
     * Returns the {@link Class} of the expected representation for handlers of the given {@code qualifiedName}.
     *
     * @param qualifiedName The {@link QualifiedName} of the handler to look for.
     * @return The {@link Class} of the expected representation for handlers of the given {@code qualifiedName}, or
     * {@code null} if no such representation is found.
     */
    public Class<?> getExpectedRepresentation(QualifiedName qualifiedName) {
        if (payloadTypes.containsKey(qualifiedName)) {
            return payloadTypes.get(qualifiedName);
        }
        for (AnnotatedEntityModel<?> child : children) {
            Class<?> payloadType = child.getExpectedRepresentation(qualifiedName);
            if (payloadType != null) {
                return payloadType;
            }
        }
        return null;
    }

    private void initializeChildren(EntityModelBuilder<E> builder) {
        ServiceLoader<EntityChildModelDefinition> childEntityDefinitions = ServiceLoader
                .load(EntityChildModelDefinition.class, entityType.getClassLoader());
        stream(ReflectionUtils.fieldsOf(entityType).spliterator(), false)
                .forEach(field -> createOptionalChildForMember(builder,
                                                               field,
                                                               childEntityDefinitions));
        stream(ReflectionUtils.methodsOf(entityType).spliterator(), false)
                .forEach(method -> createOptionalChildForMember(builder,
                                                                method,
                                                                childEntityDefinitions));
    }

    private void createOptionalChildForMember(EntityModelBuilder<E> builder,
                                              Member field,
                                              ServiceLoader<EntityChildModelDefinition> childEntityDefinitions) {
        childEntityDefinitions.forEach(definition -> definition
                .createChildDefinition(entityType, this::createChildEntityModel, field)
                .ifPresent(child -> {
                    if (child.entityModel() instanceof AnnotatedEntityModel<?> annotatedChild) {
                        children.add(annotatedChild);
                    }
                    builder.addChild(child);
                }));
    }

    private <C> AnnotatedEntityModel<C> createChildEntityModel(Class<C> clazz) {
        return new AnnotatedEntityModel<>(clazz, parameterResolverFactory, messageTypeResolver);
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedCommands() {
        return entityModel.supportedCommands();
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedCreationalCommands() {
        return entityModel.supportedCreationalCommands();
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedInstanceCommands() {
        return entityModel.supportedInstanceCommands();
    }

    @Override
    @Nonnull
    public MessageStream.Single<CommandResultMessage<?>> handleInstance(
            @Nonnull CommandMessage<?> message,
            @Nonnull E entity,
            @Nonnull ProcessingContext context
    ) {
        return entityModel.handleInstance(message, entity, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(entityModel);
        descriptor.describeProperty("entityType", entityType());
    }

    @Override
    public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        return entityModel.evolve(entity, event, context);
    }

    @Override
    @Nonnull
    public Class<E> entityType() {
        return entityType;
    }

    @Override
    @Nonnull
    public MessageStream.Single<CommandResultMessage<?>> handleCreate(CommandMessage<?> message,
                                                                      ProcessingContext context) {
        return entityModel.handleCreate(message, context);
    }
}
