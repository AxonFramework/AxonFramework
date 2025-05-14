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

package org.axonframework.eventsourcing.annotation.reflection;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.PayloadParameterResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Reflection-based implementation of the {@link EventSourcedEntityFactory} interface. This factory will look for
 * {@link EntityFactoryMethod}-annotated constructors and static methods on the entity type and its supertypes to find a
 * suitable constructor or static method to create an entity instance.
 * <p>
 * This class implements the requirements as per the {@link EntityFactoryMethod} annotation. This class is thread-safe.
 *
 * @param <E>  The type of entity to create.
 * @param <ID> The type of identifier used by the entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotationBasedEventSourcedEntityFactory<E, ID> implements EventSourcedEntityFactory<ID, E> {

    private final Context.ResourceKey<ID> ID_KEY = Context.ResourceKey.withLabel("EventSourcedEntityFactory.id");

    private final Class<E> entityType;
    private final Set<Class<? extends E>> types;
    private final Class<ID> idType;
    private final List<ScannedFactoryMethod> factoryMethods = new ArrayList<>();
    private final IdTypeParameterResolver idTypeParameterResolver = new IdTypeParameterResolver();
    private final ParameterResolverFactory resolverFactory;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Instantiate an annotation-based {@link EventSourcedEntityFactory} for the given concrete {@code entityType}. When
     * using a polymorphic entity type, you can use the
     * {@link #AnnotationBasedEventSourcedEntityFactory(Class, Class, Set, ParameterResolverFactory,
     * MessageTypeResolver)}, so that all subtypes of the entity type will be scanned for static methods and
     * constructors.
     *
     * @param entityType               The type of the entity to create. Must be concrete.
     * @param idType                   The type of the identifier used by the entity.
     * @param parameterResolverFactory The factory to use to resolve parameters.
     * @param messageTypeResolver      The factory to use to resolve the payload type.
     */
    public AnnotationBasedEventSourcedEntityFactory(@Nonnull Class<E> entityType,
                                                    @Nonnull Class<ID> idType,
                                                    @Nonnull ParameterResolverFactory parameterResolverFactory,
                                                    @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        this(entityType, idType, Collections.emptySet(), parameterResolverFactory, messageTypeResolver);
    }

    /**
     * Instantiate a reflection-based {@link EventSourcedEntityFactory} for the given super {@code entityType}, with the
     * given {@code subTypes}. The {@code subTypes} must be concrete types that extend the {@code entityType}. The
     * factory will look for static methods and constructors on the {@code subTypes} and their supertypes to find a
     * suitable constructor or static method to create an entity instance.
     *
     * @param entityType               The type of the entity to create. Can be abstract.
     * @param idType                   The type of the identifier used by the entity.
     * @param subTypes                 The concrete types that extend the {@code entityType}.
     * @param parameterResolverFactory The factory to use to resolve parameters.
     * @param messageTypeResolver      The factory to use to resolve the payload type.
     */
    public AnnotationBasedEventSourcedEntityFactory(@Nonnull Class<E> entityType,
                                                    @Nonnull Class<ID> idType,
                                                    @Nonnull Set<Class<? extends E>> subTypes,
                                                    @Nonnull ParameterResolverFactory parameterResolverFactory,
                                                    @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        this.entityType = Objects.requireNonNull(entityType, "The entityType must not be null.");
        this.types = new HashSet<>(subTypes);
        types.add(entityType);
        this.idType = Objects.requireNonNull(idType, "The idType must not be null.");

        this.resolverFactory = Objects.requireNonNull(parameterResolverFactory,
                                                      "The parameterResolverFactory must not be null.");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver,
                                                          "The messageTypeResolver must not be null.");

        initialize();
    }

    private void initialize() {
        scanMethods();
        scanConstructors();
        validate();
    }

    private void scanConstructors() {
        types.stream()
             .flatMap(t -> Arrays.stream(t.getDeclaredConstructors()))
             .filter(m -> m.isAnnotationPresent(EntityFactoryMethod.class))
             .distinct()
             .forEach(this::createAndAddInitializerExecutable);
    }

    private void scanMethods() {
        types.stream()
             .flatMap(t -> StreamSupport.stream(ReflectionUtils.methodsOf(t).spliterator(), false))
             .filter(m -> m.isAnnotationPresent(EntityFactoryMethod.class))
             .distinct()
             .forEach(this::createAndAddInitializerMethod);
    }

    private void validate() {
        if (factoryMethods.isEmpty()) {
            throw new AxonConfigurationException(
                    "No @EntityFactoryMethod present on entity. Can not initialize AnnotationBasedEventSourcedEntityFactory.");
        }
    }

    private void createAndAddInitializerMethod(Method m) {
        if (!Modifier.isStatic(m.getModifiers())) {
            throw new AxonConfigurationException("@EntityFactoryMethod must be static. Found: %s".formatted(m));
        }
        if (!m.getReturnType().isAssignableFrom(entityType)) {
            throw new AxonConfigurationException(
                    "@EntityFactoryMethod must return the entity type or a subtype. Found: [%s]".formatted(m));
        }
        createAndAddInitializerExecutable(m);
    }

    private void createAndAddInitializerExecutable(Executable c) {
        EntityFactoryMethod annotation = c.getAnnotation(EntityFactoryMethod.class);
        if (annotation == null) {
            return;
        }

        List<QualifiedName> payloadQualifiedNames = Arrays.stream(annotation.payloadQualifiedNames()).map(
                QualifiedName::new
        ).collect(Collectors.toList());
        ParameterResolver<?>[] parameterResolvers = new ParameterResolver[c.getParameterCount()];
        boolean hasMessageParameter = false;
        Class<?> concreteIdType = null;

        for (int i = 0; i < c.getParameterCount(); i++) {
            Class<?> parameterType = c.getParameterTypes()[i];

            // Check the parameter type for the ID type, and assign our special IdTypeParameterResolver
            if (concreteIdType == null && idType.isAssignableFrom(parameterType)) {
                parameterResolvers[i] = idTypeParameterResolver;
                concreteIdType = parameterType;
                continue;
            }
            if (Message.class.isAssignableFrom(parameterType)) {
                hasMessageParameter = true;
            }

            ParameterResolver<?> instance = resolverFactory.createInstance(c, c.getParameters(), i);
            if (instance == null) {
                throw new AxonConfigurationException(
                        "Could not resolve parameter [%d] of [%s]. No suitable ParameterResolver found for type [%s]"
                                .formatted(i, c, parameterType.getName()));
            }
            parameterResolvers[i] = instance;
        }

        if (payloadQualifiedNames.isEmpty()) {
            // Let's find if we have a PayloadParameterResolver
            Arrays.stream(parameterResolvers)
                  .filter(p -> p instanceof PayloadParameterResolver)
                  .findFirst()
                  .map(prr -> messageTypeResolver.resolve(prr.supportedPayloadType()))
                  .ifPresent(messageType -> payloadQualifiedNames.add(messageType.qualifiedName()));
        }

        factoryMethods.add(new ScannedFactoryMethod(c,
                                                    parameterResolvers,
                                                    payloadQualifiedNames,
                                                    concreteIdType,
                                                    hasMessageParameter));
    }

    private ScannedFactoryMethod findMostSpecificMethod(ID id, EventMessage<?> eventMessage,
                                                        ProcessingContext context) {
        Set<ScannedFactoryMethod> compatibleMethods = factoryMethods.stream()
                                                                    .filter(e -> e.supportsId(id))
                                                                    .collect(Collectors.toSet());

        // If we have an EventMessage, methods taking the payload type of the event message have precedence
        if (eventMessage != null) {
            Set<ScannedFactoryMethod> eventCompatibleConstructors = compatibleMethods
                    .stream()
                    .filter(e -> e.hasPayload(eventMessage.type().qualifiedName()))
                    .collect(Collectors.toSet());
            if (!eventCompatibleConstructors.isEmpty()) {
                compatibleMethods = eventCompatibleConstructors;
            }
        }
        if (compatibleMethods.isEmpty()) {
            throw new AxonConfigurationException(
                    "No suitable @EntityFactoryMethods found for id: [%s] and event message [%s]: [%s]"
                            .formatted(id, eventMessage, factoryMethods));
        }
        Set<ScannedFactoryMethod> matchingMethods = compatibleMethods.stream()
                                                                     .filter(e -> e.parametersMatch(eventMessage,
                                                                                                    context))
                                                                     .collect(Collectors.toSet());
        if (matchingMethods.isEmpty()) {
            throw new AxonConfigurationException(
                    "None of the @EntityFactoryMethods match the event message [%s] and context [%s]. Candidates were: [%s]"
                            .formatted(eventMessage, context, compatibleMethods));
        }
        return matchingMethods.stream()
                              .max(Comparator.comparingInt(ScannedFactoryMethod::getParameterCount))
                              .orElseThrow();
    }

    @Nullable
    @Override
    public E create(@Nonnull ID id, @Nullable EventMessage<?> firstEventMessage, @Nonnull ProcessingContext context) {
        ProcessingContext contextWithId = context.withResource(ID_KEY, id);
        return findMostSpecificMethod(id, firstEventMessage, contextWithId)
                .invoke(id, firstEventMessage, contextWithId);
    }

    /**
     * Represents a scanned factory method, ready to be invoked. This class is immutable and thread-safe.
     */
    private class ScannedFactoryMethod {

        private final Executable executable;
        private final ParameterResolver<?>[] parameterResolvers;
        private final List<QualifiedName> payloadQualifiedNames;
        private final Class<?> concreteIdType;
        private final boolean hasMessageParameter;

        private ScannedFactoryMethod(Executable executable,
                                     ParameterResolver<?>[] parameterResolvers,
                                     List<QualifiedName> payloadQualifiedNames,
                                     Class<?> concreteIdType, boolean hasMessageParameter) {
            this.hasMessageParameter = hasMessageParameter;

            ReflectionUtils.ensureAccessible(executable);
            this.executable = executable;
            this.parameterResolvers = parameterResolvers;
            this.payloadQualifiedNames = payloadQualifiedNames;
            this.concreteIdType = concreteIdType;
        }

        private E invoke(ID id, EventMessage<?> eventMessage, ProcessingContext context) {
            ProcessingContext contextWithId = context.withResource(ID_KEY, id);
            Object[] args = new Object[executable.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = parameterResolvers[i].resolveParameterValue(eventMessage, contextWithId);
            }
            return constructEntityWithArguments(args);
        }

        private boolean supportsId(ID id) {
            return concreteIdType == null || concreteIdType.isAssignableFrom(id.getClass());
        }

        private int getParameterCount() {
            return parameterResolvers.length;
        }

        private boolean parametersMatch(EventMessage<?> message, ProcessingContext processingContext) {
            return Arrays.stream(parameterResolvers)
                         .allMatch(f -> f.matches(message, processingContext));
        }

        private boolean hasPayload(QualifiedName qualifiedName) {
            if (hasMessageParameter) {
                return true;
            }
            return payloadQualifiedNames.contains(qualifiedName);
        }

        @SuppressWarnings("unchecked")
        private E constructEntityWithArguments(Object[] args) {
            try {
                return (E) switch (executable) {
                    case Constructor<?> c -> c.newInstance(args);
                    case Method method -> method.invoke(null, args);
                };
            } catch (Exception e) {
                throw new AxonConfigurationException("Failed to invoke entity initializer", e);
            }
        }

        @Override
        public String toString() {
            return ReflectionUtils.getMemberGenericString(executable);
        }
    }

    /**
     * Internal parameter resolver for the ID parameter on a {@link EntityFactoryMethod}. Will get the {@code ID_KEY}
     * resource from the context and return it as the parameter value.
     */
    private class IdTypeParameterResolver implements ParameterResolver<ID> {

        @Override
        public ID resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
            return processingContext.getResource(ID_KEY);
        }

        @Override
        public boolean matches(Message<?> message, ProcessingContext processingContext) {
            return processingContext.containsResource(ID_KEY);
        }
    }
}
