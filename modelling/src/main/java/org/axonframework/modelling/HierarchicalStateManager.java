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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link StateManager} that can load an entity from two delegates, giving preference to the child delegate and then the
 * parent. This is useful to encapsulate a set of repositories that are only relevant in a specific context, such as a
 * specific {@link org.axonframework.configuration.Module}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class HierarchicalStateManager implements StateManager {

    private final StateManager parent;
    private final StateManager child;

    private HierarchicalStateManager(StateManager parent, StateManager child) {
        this.parent = parent;
        this.child = child;
    }

    /**
     * Creates a new hierarchical {@link StateManager} that delegates to the given {@code parent} and {@code child}
     * managers, giving preference to the {@code child} manager.
     *
     * @param parent The parent {@link StateManager} to delegate if the child {@link StateManager} cannot load the
     *               entity.
     * @param child  The child {@link StateManager} to try first.
     * @return A new hierarchical {@link StateManager} that delegates to the given managers.
     */
    public static HierarchicalStateManager create(StateManager parent, StateManager child) {
        return new HierarchicalStateManager(parent, child);
    }

    @Override
    public <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(@Nonnull Class<T> type,
                                                                           @Nonnull I id,
                                                                           @Nonnull ProcessingContext context) {
        //noinspection unchecked
        Class<I> idClass = (Class<I>) id.getClass();
        AsyncRepository<I, T> repository = repository(type, idClass);
        if (repository != null) {
            return repository.load(id, context);
        }
        throw new MissingRepositoryException(id.getClass(), type);
    }

    @Override
    public Set<Class<?>> registeredEntities() {
        HashSet<Class<?>> classes = new HashSet<>();
        classes.addAll(parent.registeredEntities());
        classes.addAll(child.registeredEntities());
        return classes;
    }

    @Override
    public Set<Class<?>> registeredIdsFor(@Nonnull Class<?> entityType) {
        HashSet<Class<?>> classes = new HashSet<>();
        classes.addAll(parent.registeredIdsFor(entityType));
        classes.addAll(child.registeredIdsFor(entityType));
        return classes;
    }

    @Override
    public <I, T> AsyncRepository<I, T> repository(@Nonnull Class<T> entityType, @Nonnull Class<I> idType) {
        AsyncRepository<I, T> childRepository = child.repository(entityType, idType);
        if (childRepository != null) {
            return childRepository;
        }
        return parent.repository(entityType, idType);
    }
}
