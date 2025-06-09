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
import org.axonframework.commandhandling.annotation.CommandMessageHandlingMember;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.EntityModelBuilder;
import org.axonframework.modelling.entity.PolymorphicEntityModel;
import org.axonframework.modelling.entity.PolymorphicEntityModelBuilder;
import org.axonframework.modelling.entity.SimpleEntityModel;
import org.axonframework.modelling.entity.child.EntityChildModel;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;
import static org.axonframework.messaging.annotation.AnnotatedHandlerInspector.inspectType;

/**
 * An {@link EntityModel} implementation that uses reflection to inspect the entity. It will detect annotated command-
 * and event-handling methods, as well as child entities annotated with {@link EntityMember}. Everything that is
 * discovered is then registered to a delegate {@link EntityModel}, so that essentially a declared model is built from
 * annotations of which it's structure is clearly defined.
 * <p>
 * Besides normal {@link EntityModel} operations, this model also provides a means to
 * {@link #getExpectedRepresentation(QualifiedName) get the expected representation} of a command or event handler based
 * on the {@link QualifiedName} of the message type. This is useful for determining the payload type of a command or
 * event handler when multiple handlers are present for the same message type.
 * <p>
 * NOTE: This class is a complete rewrite of the pre-5.0.0 {@code AnnotatedAggregateMetaModelFactory}. Both scan the
 * class for annotated methods and fields, but the AnnotatedEntityModel dropped aggregate versioning (conflict
 * resolution), no longer required an id in the entity, and creates a declarative model instead of relying on reflection
 * at runtime.
 *
 * @param <E> The type of entity this model describes.
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @since 3.1.0
 */
public class AnnotatedEntityModel<E> implements EntityModel<E>, DescribableComponent {

    private final Class<E> entityType;
    private final EntityModel<E> entityModel;
    private final ParameterResolverFactory parameterResolverFactory;
    private final MessageTypeResolver messageTypeResolver;
    private final Map<QualifiedName, Class<?>> payloadTypes = new HashMap<>();
    private final List<AnnotatedEntityModel<?>> concreteTypeModels = new LinkedList<>();
    private final List<AnnotatedEntityModel<?>> childModels = new LinkedList<>();
    private final List<QualifiedName> commandsToSkip;


    /**
     * Instantiate an annotated {@link EntityModel} of a concrete entity type.
     *
     * @param entityType               The concrete entity type this model describes.
     * @param parameterResolverFactory The {@link ParameterResolverFactory} to use for resolving parameters.
     * @param messageTypeResolver      The {@link MessageTypeResolver} to use for resolving message types from payload
     *                                 classes.
     * @param <T>                      The type of entity this model describes.
     * @return An annotated {@link EntityModel} backed by a {@link SimpleEntityModel} for the given entity type.
     */
    public static <T> AnnotatedEntityModel<T> forConcreteType(
            @Nonnull Class<T> entityType,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        return new AnnotatedEntityModel<>(entityType,
                                          Set.of(),
                                          parameterResolverFactory,
                                          messageTypeResolver,
                                          List.of());
    }

    /**
     * Instantiate an annotated {@link EntityModel} of a polymorphic entity type. At least one concrete type must be
     * supplied, as this model is meant to describe a polymorphic entity type with multiple concrete
     * implementations.
     *
     * @param entityType               The polymorphic entity type this model describes.
     * @param concreteTypes            The concrete types of the polymorphic entity type.
     * @param parameterResolverFactory The {@link ParameterResolverFactory} to use for resolving parameters.
     * @param messageTypeResolver      The {@link MessageTypeResolver} to use for resolving message types from payload
     *                                 classes.
     * @param <T>                      The type of the polymorphic entity.
     * @return An annotated {@link EntityModel} backed by a {@link PolymorphicEntityModel} for the given entity type.
     */
    public static <T> AnnotatedEntityModel<T> forPolymorphicType(
            @Nonnull Class<T> entityType,
            @Nonnull Set<Class<? extends T>> concreteTypes,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        requireNonNull(concreteTypes, "The concreteTypes may not be null.");
        Assert.isTrue(!concreteTypes.isEmpty(),
                      () -> "The concreteTypes set must not be empty for a polymorphic entity type.");
        return new AnnotatedEntityModel<>(entityType,
                                          concreteTypes,
                                          parameterResolverFactory,
                                          messageTypeResolver,
                                          List.of());
    }

    /**
     * Instantiate an annotated {@link EntityModel} of an entity type. If the supplied {@code concreteTypes} is not
     * empty, the entity type is considered polymorphic and will be a {@link PolymorphicEntityModel}. If no concrete
     * types are supplied, the entity type is considered concrete and will be a {@link SimpleEntityModel}
     *
     * @param entityType               The concrete entity type this model describes.
     * @param parameterResolverFactory The {@link ParameterResolverFactory} to use for resolving parameters.
     * @param messageTypeResolver      The {@link MessageTypeResolver} to use for resolving message types from payload
     *                                 classes.
     * @param concreteTypes            The concrete types of the polymorphic entity type.
     * @param commandsToSkip           The commands to skip when initializing the model. This is useful to prevent
     *                                 concrete implementations from registering commands that are already registered by
     *                                 the abstract entity type, as this will lead to problems.
     */
    private AnnotatedEntityModel(
            @Nonnull Class<E> entityType,
            @Nonnull Set<Class<? extends E>> concreteTypes,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver,
            @Nonnull List<QualifiedName> commandsToSkip
    ) {
        this.commandsToSkip = requireNonNull(commandsToSkip, "The commandsToSkip may not be null.");
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
        AnnotatedHandlerInspector<E> inspected = inspectType(entityType, parameterResolverFactory);
        PolymorphicEntityModelBuilder<E> builder = PolymorphicEntityModel.forSuperType(entityType);
        builder.entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(entityType, inspected));
        initializeChildren(builder);
        // Commands that are present on the parent entity should not be registered again on the concrete
        // types. So we tell concrete types to skip these commands.
        LinkedList<QualifiedName> registeredCommands = initializeDetectedHandlers(builder, inspected);
        concreteTypes.forEach(concreteType -> {
            List<QualifiedName> mergedQualifiedNames = Stream.concat(commandsToSkip.stream(),
                                                                     registeredCommands.stream()).toList();
            AnnotatedEntityModel<? extends E> createdConcreteEntityModel = new AnnotatedEntityModel<>(
                    concreteType, Set.of(), parameterResolverFactory, messageTypeResolver, mergedQualifiedNames
            );
            concreteTypeModels.add(createdConcreteEntityModel);
            builder.addConcreteType(createdConcreteEntityModel);
        });
        return builder.build();
    }

    private EntityModel<E> initializeEntityModel(EntityModelBuilder<E> builder, Class<E> entityType) {
        AnnotatedHandlerInspector<E> inspected = inspectType(entityType, parameterResolverFactory);
        builder.entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(entityType, inspected));
        initializeDetectedHandlers(builder, inspected);
        initializeChildren(builder);
        return builder.build();
    }

    private LinkedList<QualifiedName> initializeDetectedHandlers(
            EntityModelBuilder<E> builder, AnnotatedHandlerInspector<E> inspected
    ) {
        LinkedList<QualifiedName> registeredCommands = new LinkedList<>();
        inspected.getHandlers(entityType)
                 .filter(h -> h.canHandleMessageType(CommandMessage.class)
                         || h.canHandleMessageType(EventMessage.class))
                 .filter(h -> h.unwrap(Method.class).map(m -> !Modifier.isAbstract(m.getModifiers())).orElse(false))
                 .forEach(handler -> {
                     QualifiedName qualifiedName = messageTypeResolver.resolveOrThrow(handler.payloadType())
                                                                      .qualifiedName();
                     if (commandsToSkip.contains(qualifiedName)) {
                         // This command is already registered by the abstract entity type, so we skip it.
                         return;
                     }

                     addPayloadTypeFromHandler(qualifiedName, handler);
                     addCommandHandlerToModel(builder, handler, qualifiedName, registeredCommands);
                 });
        return registeredCommands;
    }

    private void addCommandHandlerToModel(EntityModelBuilder<E> builder, MessageHandlingMember<? super E> handler,
                                          QualifiedName qualifiedName, LinkedList<QualifiedName> registeredCommands) {
        if (!(handler instanceof CommandMessageHandlingMember<? super E> commandMember)) {
            return;
        }
        registeredCommands.add(qualifiedName);
        if (commandMember.isFactoryHandler()) {
            builder.creationalCommandHandler(qualifiedName, ((command, context) -> handler
                    .handle(command, context, null)
                    .<CommandResultMessage<?>>mapMessage(GenericCommandResultMessage::new)
                    .first()));
        } else {
            builder.instanceCommandHandler(qualifiedName, ((command, entity, context) -> handler
                    .handle(command, context, entity)
                    .<CommandResultMessage<?>>mapMessage(GenericCommandResultMessage::new)
                    .first()));
        }
    }

    private void addPayloadTypeFromHandler(QualifiedName qualifiedName, MessageHandlingMember<?> handler) {
        if (payloadTypes.containsKey(qualifiedName) && !payloadTypes.get(qualifiedName).equals(handler.payloadType())) {
            throw new AxonConfigurationException(
                    "The scanned message handler methods expect different payload types for the same message type. Message of qualified name ["
                            + qualifiedName + "] declares both [" + payloadTypes.get(qualifiedName) + "] and ["
                            + handler.payloadType() + "] as wanted representations");
        }
        payloadTypes.put(qualifiedName, handler.payloadType());
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
        for (AnnotatedEntityModel<?> concreteType : concreteTypeModels) {
            Class<?> payloadType = concreteType.getExpectedRepresentation(qualifiedName);
            if (payloadType != null) {
                return payloadType;
            }
        }
        for (AnnotatedEntityModel<?> child : childModels) {
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
        List<Method> methods = stream(ReflectionUtils.methodsOf(entityType).spliterator(), false).toList();
        List<Field> fields = stream(ReflectionUtils.fieldsOf(entityType).spliterator(), false).toList();

        methods.forEach(method -> createOptionalChildForMember(builder, method, childEntityDefinitions));

        if (entityType.isRecord()) {
            // Annotated record fields have both a backing `Field` and `Method`, so we can filter out any field
            // that has a corresponding method, or we get duplicates.
            fields = fields.stream().filter(field -> methods
                                   .stream()
                                   .noneMatch(method -> method.getName().equals(field.getName())
                                           && method.getParameterCount() == 0
                                           && method.getReturnType().equals(field.getType())))
                           .toList();
        }
        fields.forEach(field -> createOptionalChildForMember(builder, field, childEntityDefinitions));
    }

    private void createOptionalChildForMember(EntityModelBuilder<E> builder,
                                              Member field,
                                              ServiceLoader<EntityChildModelDefinition> childEntityDefinitions) {
        List<EntityChildModel<Object, E>> childModels = childEntityDefinitions
                .stream()
                .map(ServiceLoader.Provider::get)
                .map(d -> d.createChildDefinition(entityType, this::createChildEntityModel, field))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (childModels.size() > 1) {
            throw new IllegalStateException("Multiple child entity definitions found for member [" + field
                                                    + "] of entity type [" + entityType + "]. Please ensure only one "
                                                    + "definition is present for this member. Found definitions: "
                                                    + childModels);
        }
        if (childModels.size() == 1) {
            var child = childModels.getFirst();
            if (child.entityModel() instanceof AnnotatedEntityModel<?> annotatedChild) {
                this.childModels.add(annotatedChild);
            }
            builder.addChild(child);
        }
    }

    /**
     * This is the {@link AnnotatedEntityModelFactory} method to create a child entity model for the given
     * {@code clazz}, while using the same resources as its parent model (this instance).
     *
     * @param clazz The class of the child entity to create a model for.
     * @param <C>   The type of the child entity to create a model for.
     * @return An {@code AnnotatedEntityModel} for the given {@code clazz}, using the same
     * {@link ParameterResolverFactory} and {@link MessageTypeResolver} as this instance.
     */
    private <C> AnnotatedEntityModel<C> createChildEntityModel(Class<C> clazz) {
        return new AnnotatedEntityModel<>(clazz, Set.of(), parameterResolverFactory, messageTypeResolver, List.of());
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedCommands() {
        return Collections.unmodifiableSet(entityModel.supportedCommands());
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedCreationalCommands() {
        return Collections.unmodifiableSet(entityModel.supportedCreationalCommands());
    }

    @Override
    @Nonnull
    public Set<QualifiedName> supportedInstanceCommands() {
        return Collections.unmodifiableSet(entityModel.supportedInstanceCommands());
    }

    @Override
    @Nonnull
    public MessageStream.Single<CommandResultMessage<?>> handleCreate(@Nonnull CommandMessage<?> message,
                                                                      @Nonnull ProcessingContext context) {
        return entityModel.handleCreate(message, context);
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
}
