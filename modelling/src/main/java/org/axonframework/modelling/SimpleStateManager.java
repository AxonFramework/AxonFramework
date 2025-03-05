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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple implementation of the {@link StateManager}. Keeps a list of {@link RegisteredStateRepository}s to resolve the
 * correct {@link AsyncRepository} for a given model type.
 *
 * @author Mitchell Herrijgers
 * @see StateManager
 * @since 5.0.0
 */
public class SimpleStateManager implements StateManager, DescribableComponent {

    private final List<RegisteredStateRepository<?, ?>> repositories = new CopyOnWriteArrayList<>();
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
    public <I, M> SimpleStateManager register(
            @Nonnull Class<I> idType,
            @Nonnull Class<M> stateType,
            @Nonnull AsyncRepository<I, M> repository) {
        if (!getRepositoriesForStateType(stateType).isEmpty()) {
            throw new StateTypeAlreadyRegisteredException(stateType);
        }
        repositories.add(new RegisteredStateRepository<>(idType, stateType, repository));
        return this;
    }

    @Nonnull
    @Override
    public <I, M> CompletableFuture<M> load(@Nonnull Class<M> type, @Nonnull I id, ProcessingContext context) {
        var definitions = getRepositoriesForStateType(type);
        if (definitions.isEmpty()) {
            return CompletableFuture.failedFuture(new MissingRepositoryException(type));
        }
        var repository = definitions.getFirst().repository();
        return repository.load(id, context).thenApply(ManagedEntity::entity);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("repositories", repositories);
    }

    @SuppressWarnings("unchecked") // The cast is checked in the stream
    private <I, M> List<RegisteredStateRepository<I, M>> getRepositoriesForStateType(@Nonnull Class<M> stateType) {
        return repositories
                .stream()
                .filter(md -> md.stateType().equals(stateType))
                .map(md -> (RegisteredStateRepository<I, M>) md)
                .toList();
    }

    private record RegisteredStateRepository<I, M>(
            Class<I> idClass,
            Class<M> stateType,
            AsyncRepository<I, M> repository
    ) {

    }
}
