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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

/**
 * {@link EventSourcedEntityFactory} implementation which uses a constructor to create a new instance of an entity. The
 * constructor can either have a single argument of the same type as the identifier, a single argument of the first
 * event payload type, or no arguments at all. Note that arguments can not be combined, so a constructor with both an ID
 * and an event payload is not supported.
 *
 * @param <E> The type of the entity to create.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ConstructorBasedEventSourcedEntityFactory<E> implements EventSourcedEntityFactory<Object, E> {

    private final Class<?> entityType;

    /**
     * Instantiate a constructor-based {@link EventSourcedEntityFactory} for the given {@code entityType}.
     *
     * @param entityType The type of the entity to create.
     */
    public ConstructorBasedEventSourcedEntityFactory(Class<E> entityType) {
        this.entityType = entityType;
    }

    @Override
    public E createEntityBasedOnFirstEventMessage(@Nonnull Object id,
                                                  @Nonnull EventMessage<?> firstEventMessage) {
        try {
            Optional<Constructor<?>> constructorWithEventPayload = Arrays
                    .stream(entityType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 1)
                    .filter(constructor -> constructor.getParameterTypes()[0].isInstance(firstEventMessage.getPayload()))
                    .findFirst();
            if (constructorWithEventPayload.isPresent()) {
                Constructor<?> constructor = constructorWithEventPayload.get();
                ReflectionUtils.ensureAccessible(constructor);
                //noinspection unchecked
                return (E) constructor.newInstance(firstEventMessage.getPayload());
            }

            // Okay, and with EventMessage?
            Optional<Constructor<?>> constructorWithEventMessage = Arrays
                    .stream(entityType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 1)
                    .filter(constructor -> EventMessage.class.isAssignableFrom(constructor.getParameterTypes()[0]))
                    .findFirst();
            if (constructorWithEventMessage.isPresent()) {
                Constructor<?> constructor = constructorWithEventMessage.get();
                ReflectionUtils.ensureAccessible(constructor);
                //noinspection unchecked
                return (E) constructor.newInstance(firstEventMessage);
            }

            return createEmptyEntity(id);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public E createEmptyEntity(@Nonnull Object id) {
        try {
            // Okay, with ID
            Optional<Constructor<?>> constructorWithId = Arrays
                    .stream(entityType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 1)
                    .filter(constructor -> constructor.getParameterTypes()[0].isInstance(id))
                    .findFirst();
            if (constructorWithId.isPresent()) {
                Constructor<?> constructor = constructorWithId.get();
                ReflectionUtils.ensureAccessible(constructor);
                //noinspection unchecked
                return (E) constructor.newInstance(id);
            }

            // Finally, try the zero-argument constructor
            Optional<Constructor<?>> zeroArgConstructor = Arrays
                    .stream(entityType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 0)
                    .findFirst();
            if (zeroArgConstructor.isPresent()) {
                Constructor<?> constructor = zeroArgConstructor.get();
                ReflectionUtils.ensureAccessible(constructor);
                //noinspection unchecked
                return (E) constructor.newInstance();
            }

            // If we reach this point, we have no constructor that matches the ID or event message
            // Throw an exception
            throw new IllegalArgumentException(
                    "No suitable constructor found for entity of type [%s] with ID of type [%s]"
                            .formatted(entityType.getName(), id.getClass().getName())
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
