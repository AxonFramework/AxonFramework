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

package org.axonframework.modelling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple implementation of the {@link StateManager}. Keeps a map of registered repositories and uses them to load and
 * save entities.
 *
 * @author Mitchell Herrijgers
 * @see StateManager
 * @since 5.0.0
 */
public class SimpleStateManager implements StateManager, DescribableComponent {

    private final Map<Class<?>, RegisteredRepository<?, ?>> repositories = new ConcurrentHashMap<>();
    private final String name;

    /**
     * Constructs a new simple {@link StateManager} instance.
     *
     * @param name The name of the component, used for {@link DescribableComponent describing} the component.
     */
    public static SimpleStateManager create(@Nonnull String name) {
        BuilderUtils.assertNonBlank(name, "Name may not be blank");
        return new SimpleStateManager(name);
    }

    private SimpleStateManager(@Nonnull String name) {
        this.name = name;
    }

    @Override
    public <I, T> SimpleStateManager register(
            @Nonnull Class<I> idType,
            @Nonnull Class<T> stateType,
            @Nonnull AsyncRepository<I, T> repository) {
        if (repositories.containsKey(stateType)) {
            throw new StateTypeAlreadyRegisteredException(stateType);
        }
        repositories.put(stateType, new RegisteredRepository<>(idType, stateType, repository));
        return this;
    }

    @Nonnull
    @Override
    public <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(
            @Nonnull Class<T> type,
            @Nonnull I id,
            @Nonnull ProcessingContext context
    ) {
        //noinspection unchecked
        RegisteredRepository<I, T> definition = (RegisteredRepository<I, T>) repositories.get(type);
        if (definition == null) {
            return CompletableFuture.failedFuture(new MissingRepositoryException(type));
        }
        if(!definition.idClass().isAssignableFrom(id.getClass())) {
            return CompletableFuture.failedFuture(new IdTypeMismatchException(
                    definition.idClass(), id.getClass()
            ));
        }
        return definition.repository().load(id, context);
    }

    @Override
    public Set<Class<?>> registeredTypes() {
        return repositories.keySet();
    }

    @Override
    public <T> AsyncRepository<?, T> repository(Class<T> type) {
        //noinspection unchecked
        return (AsyncRepository<?, T>) repositories.get(type).repository();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("repositories", repositories);
    }

    private record RegisteredRepository<I, M>(
            Class<I> idClass,
            Class<M> stateType,
            AsyncRepository<I, M> repository
    ) {

    }
}
