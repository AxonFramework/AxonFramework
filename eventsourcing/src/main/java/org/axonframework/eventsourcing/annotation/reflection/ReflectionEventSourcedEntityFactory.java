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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Reflection-based implementation of the {@link EventSourcedEntityFactory} interface. This factory will look for
 * constructors and static methods on the entity type and its supertypes to find a suitable constructor or static method
 * to create an entity instance.
 * <p>
 * The constructor or factory method can declare the following parameters in any order:
 * <ul>
 *     <li>One parameter of the entity identifier type.</li>
 *     <li>One parameter of the {@link EventMessage} type.</li>
 *     <li>One parameter of the payload type.</li>
 *     <li>Any number of parameters of types that can be resolved from the {@link Configuration}.</li>
 * </ul>
 * Any parameter that is not an identifier, {@link EventMessage} or configuration component is considered the
 * payload type.
 * <p>
 * The most specific constructor or static method in the circumstance will be used to create the entity instance. The constructor or static
 * method must return an instance of the entity type or a subtype of it.
 * <p>
 * If an entity is created with an {@link EventMessage}, a constructor or static method with a parameter of the
 * {@link EventMessage} type or payload will be preferred over a constructor or static method without.
 * <p>
 * If there is no {@link EventMessage}, or no constructor or static method with a parameter of the
 * {@link EventMessage} type or payload, the constructor or static method with a parameter of the entity identifier
 * type will be preferred over a constructor or static method without. The amount of configuration parameters
 * is not considered.
 * <p>
 * This class is thread-safe.
 *
 * @param <E>  The type of entity to create.
 * @param <ID> The type of identifier used by the entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ReflectionEventSourcedEntityFactory<E, ID> implements EventSourcedEntityFactory<ID, E> {

    private final Class<E> entityType;
    private final Set<Class<? extends E>> types;
    private final Configuration configuration;
    private final Class<ID> idType;
    private final List<WrappedExecutable> executables = new ArrayList<>();

    /**
     * Instantiate a reflection-based {@link EventSourcedEntityFactory} for the given concrete {@code entityType}. When
     * using a polymorphic entity type, you can use the
     * {@link #ReflectionEventSourcedEntityFactory(Class, Class, Set, Configuration)}, as all subtypes of the entity
     * type will be scanned for static methods and constructors.
     *
     * @param entityType    The type of the entity to create. Must be concrete.
     * @param idType        The type of the identifier used by the entity.
     * @param configuration The configuration to use to resolve parameters.
     */
    public ReflectionEventSourcedEntityFactory(@Nonnull Class<E> entityType,
                                               @Nonnull Class<ID> idType,
                                               @Nonnull Configuration configuration) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.types = Set.of(Objects.requireNonNull(entityType, "entityType must not be null"));
        this.idType = Objects.requireNonNull(idType, "idType must not be null");
        initialize();
    }

    /**
     * Instantiate a reflection-based {@link EventSourcedEntityFactory} for the given super {@code entityType}, with the
     * given {@code subTypes}. The {@code subTypes} must be concrete types that extend the {@code entityType}. The
     * factory will look for static methods and constructors on the {@code subTypes} and their supertypes to find a
     * suitable constructor or static method to create an entity instance.
     *
     * @param entityType    The type of the entity to create. Can be abstract.
     * @param idType        The type of the identifier used by the entity.
     * @param subTypes      The concrete types that extend the {@code entityType}.
     * @param configuration The configuration to use to resolve parameters.
     */
    public ReflectionEventSourcedEntityFactory(@Nonnull Class<E> entityType,
                                               @Nonnull Class<ID> idType,
                                               @Nonnull Set<Class<? extends E>> subTypes,
                                               @Nonnull Configuration configuration
    ) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.types = Objects.requireNonNull(subTypes, "subTypes must not be null");
        this.idType = Objects.requireNonNull(idType, "idType must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        initialize();
    }

    private void initialize() {
        // Get all distinct static methods of all types and their supertypes
        types.stream()
             .flatMap(t -> StreamSupport.stream(ReflectionUtils.methodsOf(t).spliterator(), false))
             .distinct()
             .forEach(this::createAndAddInitializerMethod);

        // Get the constructors of the concrete types only
        types.stream()
             .flatMap(t -> Arrays.stream(t.getDeclaredConstructors()))
             .distinct()
             .forEach(this::createAndAddInitializerExecutable);
    }

    private void createAndAddInitializerMethod(Method m) {
        if (!Modifier.isStatic(m.getModifiers())) {
            return;
        }
        if (!m.getReturnType().isAssignableFrom(entityType)) {
            return;
        }
        createAndAddInitializerExecutable(m);
    }

    private void createAndAddInitializerExecutable(Executable c) {
        int idIndex = -1;
        int messageIndex = -1;
        int payloadIndex = -1;
        for (int i = 0; i < c.getParameterCount(); i++) {
            Class<?> parameterType = c.getParameterTypes()[i];

            // Check the parameter type for the ID type
            if (idType.isAssignableFrom(parameterType)) {
                if (idIndex != -1) {
                    throw new IllegalStateException(
                            "EntityInitializer must have only one ID parameter, but found: " + c);
                }
                idIndex = i;
                continue;
            }

            // Check if someone is trying to use the EventMessage type
            if (EventMessage.class.isAssignableFrom(parameterType)) {
                if (messageIndex != -1) {
                    throw new IllegalStateException(
                            "EntityInitializer must have only one EventMessage parameter, but found: " + c);
                }
                messageIndex = i;
                continue;
            }

            if (configuration.getOptionalComponent(parameterType).isPresent()) {
                // Injection of a configuration component, valid
                continue;
            }

            // Now, we should be left with one parameter; the payload
            // If we already had a payload, there are multiple parameters, and thus not a valid constructor.
            // This is probably an overloaded user constructor.
            if (payloadIndex != -1) {
                return;
            }
            payloadIndex = i;
        }

        ReflectionUtils.ensureAccessible(c);
        executables.add(new WrappedExecutable(c, idIndex, messageIndex, payloadIndex));
    }

    @Nonnull
    @Override
    public E createFromFirstEvent(@Nonnull ID id,
                                  @Nonnull EventMessage<?> firstEventMessage) {
        return findExactlyOneMostImportantExecutable(id, firstEventMessage)
                .map(wrappedExecutable -> wrappedExecutable.invoke(id, firstEventMessage))
                .orElseGet(() -> {
                    E emptyEntity = createEmptyEntity(id);
                    if (emptyEntity != null) {
                        return emptyEntity;
                    }

                    throw new IllegalStateException("Entity must be non-null after first event");
                });
    }

    private Optional<WrappedExecutable> findExactlyOneMostImportantExecutable(ID id, EventMessage<?> eventMessage) {
        List<WrappedExecutable> sortedList = executables
                .stream()
                .filter(e -> e.getOrder(id, eventMessage) != -1)
                .sorted(Comparator.<WrappedExecutable>comparingInt(e -> e.getOrder(id, eventMessage)).reversed())
                .toList();
        if (sortedList.isEmpty()) {
            return Optional.empty();
        }
        if (sortedList.size() == 1) {
            return Optional.of(sortedList.getFirst());
        }
        int firstOrder = sortedList.get(0).getOrder(id, eventMessage);
        int secondOrder = sortedList.get(1).getOrder(id, eventMessage);
        if (firstOrder == secondOrder) {
            throw new IllegalStateException(
                    "Multiple suitable executables found for id: [" + id + "] and event: [" + eventMessage + "]: [" +
                            sortedList.stream().filter(e -> e.getOrder(id, eventMessage) == firstOrder)
                                      .toList() + "]");
        }
        return Optional.of(sortedList.getFirst());
    }

    @Override
    public E createEmptyEntity(@Nonnull ID id) {
        return findExactlyOneMostImportantExecutable(id, null)
                .map(wrappedExecutable -> wrappedExecutable.invoke(id, null))
                .orElse(null);
    }

    /**
     * Wrapper for an {@link Executable} that allows us to determine the order of the executable based on the parameters
     * it has, while not scanning reflection again.
     */
    private class WrappedExecutable {

        private final Executable executable;
        private final int idParameterIndex;
        private final Class<?> idType;
        private final int messageParameterIndex;
        private final int payloadParameterIndex;
        private final Class<?> payloadType;

        private WrappedExecutable(Executable executable, int idParameterIndex, int messageParameterIndex,
                                  int payloadParameterIndex) {
            this.executable = executable;
            this.idParameterIndex = idParameterIndex;
            this.idType = idParameterIndex != -1 ? executable.getParameters()[idParameterIndex].getType() : null;
            this.messageParameterIndex = messageParameterIndex;
            this.payloadParameterIndex = payloadParameterIndex;
            this.payloadType = payloadParameterIndex != -1
                    ? executable.getParameters()[payloadParameterIndex].getType()
                    : null;
        }

        private int getOrder(ID id, EventMessage<?> eventMessage) {
            int order = 0;
            if (executable.getDeclaringClass() != entityType) {
                // This is a concrete type, so we give it a much higher score than the abstract type
                order += 10000;
            }
            if (eventMessage == null && (messageParameterIndex != -1 || payloadParameterIndex != -1)) {
                // If we have a message parameter, but no event message, we can't use this executable
                return -1;
            }
            if (eventMessage != null) {
                if (payloadType != null) {
                    if (payloadType.isAssignableFrom(eventMessage.getPayload().getClass())) {
                        order += 1000;
                    } else {
                        // Other payload type, not assignable
                        return -1;
                    }
                }
                if (messageParameterIndex != -1) {
                    order += 100;
                }
            }
            if (idType != null) {
                if (idType.isAssignableFrom(id.getClass())) {
                    order += 10;
                } else {
                    // Other ID type, not assignable
                    return -1;
                }
            }

            return order;
        }

        private E invoke(ID id, EventMessage<?> eventMessage) {
            Object[] args = new Object[executable.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                Class<?> parameterType = executable.getParameterTypes()[i];
                if (idParameterIndex == i) {
                    args[i] = id;
                } else if (messageParameterIndex == i) {
                    args[i] = eventMessage;
                } else if (payloadParameterIndex == i) {
                    args[i] = eventMessage.getPayload();
                } else {
                    if (parameterType.isAssignableFrom(Configuration.class)) {
                        args[i] = configuration;
                    } else {
                        args[i] = configuration.getComponent(parameterType);
                    }
                }
            }
            return constructEntityWithArguments(args);
        }

        @SuppressWarnings("unchecked")
        private E constructEntityWithArguments(Object[] args) {
            try {
                if (executable instanceof Constructor<?> c) {
                    return (E) c.newInstance(args);
                }
                if (executable instanceof Method method) {
                    return (E) method.invoke(null, args);
                }
                throw new IllegalStateException(
                        "EntityInitializer must be a constructor or static method, but found: " + executable);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke entity initializer", e);
            }
        }

        @Override
        public String toString() {
            return ReflectionUtils.getMemberGenericString(executable);
        }
    }
}
