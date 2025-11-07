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

package org.axonframework.modelling.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityIdResolutionException;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.StateManager;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ParameterResolver} implementation that loads an entity from the {@link StateManager} of the given
 * {@link Configuration}.
 * <p>
 * The entity is loaded based on the id resolved from the message using the given {@link EntityIdResolver}. Can either
 * load the {@link org.axonframework.modelling.repository.ManagedEntity} or just the entity itself.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
class InjectEntityParameterResolver implements ParameterResolver<Object> {

    private final Configuration configuration;
    private final Class<?> type;
    private final EntityIdResolver<?> identifierResolver;
    private final boolean managedEntity;

    /**
     * Instantiate a {@link ParameterResolver} that loads an entity of {@code type} using the given
     * {@code stateManager}, resolving the needed id using the {@code identifierResolver}.
     * <p>
     * This constructor depends on the {@link Configuration} instead of the {@link StateManager} to prevent circular
     * dependencies during creation of message handlers. For example, if the repository uses an annotation-based event
     * state applier, it would construct methods, which would then require the {@link StateManager} to be created during
     * the construction of the parameter resolvers. This would lead to a circular dependency.
     *
     * @param configuration      The {@link Configuration} from which a {@link StateManager} can be retrieved to load
     *                           the entity.
     * @param type               The type of the entity to load.
     * @param identifierResolver The {@link EntityIdResolver} to resolve the id of the entity.
     */
    public InjectEntityParameterResolver(
            @Nonnull Configuration configuration,
            @Nonnull Class<?> type,
            @Nonnull EntityIdResolver<?> identifierResolver,
            boolean managedEntity
    ) {
        this.configuration = requireNonNull(configuration, "The Configuration is required");
        this.type = requireNonNull(type, "The type is required");
        this.identifierResolver = requireNonNull(identifierResolver, "The ModelIdentifierResolver is required");
        this.managedEntity = managedEntity;
    }

    @Nonnull
    @Override
    public CompletableFuture<Object> resolveParameterValue(@Nonnull ProcessingContext context) {
        Message message = Message.fromContext(context);

        try {
            Object resolvedId = identifierResolver.resolve(message, context);
            StateManager stateManager = configuration.getComponent(StateManager.class);
            if (managedEntity) {
                // Safe cast: widening from CompletableFuture<T> to CompletableFuture<Object>
                // Double cast through wildcard avoids unchecked cast warnings
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> castCompletableFuture = (CompletableFuture<Object>) (CompletableFuture<?>) stateManager
                        .loadManagedEntity(type, resolvedId, context);
                return castCompletableFuture;
            }
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> castCompletableFuture = (CompletableFuture<Object>) stateManager.loadEntity(type, resolvedId, context);
            return castCompletableFuture;
        } catch (EntityIdResolutionException e) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Unable to inject entity parameter of type [%s] because [%s] was unable to resolve an entity id from [%s]"
                                    .formatted(type, identifierResolver, message),
                            e
                    )
            );
        }
    }

    @Override
    public boolean matches(@Nonnull ProcessingContext context) {
        return Message.fromContext(context) != null;
    }
}
