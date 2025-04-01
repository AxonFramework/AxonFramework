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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.axonframework.common.ConstructorUtils.factoryForTypeWithOptionalArgument;

/**
 * {@link EventSourcedEntityFactory} implementation which uses a constructor to create a new instance of an entity. The
 * constructor can either have a single argument of the same type as the identifier, or no arguments at all.
 *
 * @param <M> The type of the entity to create.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ConstructorBasedEventSourcedEntityFactory<M>
        implements EventSourcedEntityFactory<Object, M>, DescribableComponent {

    private final Class<M> entityType;
    private final Map<Class<?>, Function<Object, M>> constructorCache = new ConcurrentHashMap<>();

    /**
     * Create an {@link EventSourcedEntityFactory} for the given {@code entityType} based on its constructors.
     * @param entityType The type of entity to create.
     */
    public ConstructorBasedEventSourcedEntityFactory(@Nonnull Class<M> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public M createEntity(@Nonnull Class<M> entityType, @Nonnull Object id) {
        return constructorCache
                .computeIfAbsent(id.getClass(),
                                 (i) -> factoryForTypeWithOptionalArgument(entityType, i))
                .apply(id);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType.getName());
        descriptor.describeProperty("constructorCache", constructorCache);
    }
}
