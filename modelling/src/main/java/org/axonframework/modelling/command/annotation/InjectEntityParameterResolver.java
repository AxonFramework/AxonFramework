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

package org.axonframework.modelling.command.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ParameterResolver} implementation that loads an entity from the {@link StateManager}. The entity is loaded
 * based on the id resolved from the message using the given {@link EntityIdResolver}.
 * <p>
 * Can either load the {@link org.axonframework.modelling.repository.ManagedEntity} or just the entity itself.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
class InjectEntityParameterResolver implements ParameterResolver<Object> {

    private final StateManager stateManager;
    private final Class<?> type;
    private final EntityIdResolver<?> identifierResolver;
    private final boolean managedEntity;

    /**
     * Instantiate a {@link ParameterResolver} that loads an entity of {@code type} using the given
     * {@code stateManager}, resolving the needed id using the {@code identifierResolver}.
     *
     * @param stateManager       The {@link StateManager} to load the entity from.
     * @param type               The type of the entity to load.
     * @param identifierResolver The {@link EntityIdResolver} to resolve the id of the entity.
     */
    public InjectEntityParameterResolver(
            @Nonnull StateManager stateManager,
            @Nonnull Class<?> type,
            @Nonnull EntityIdResolver<?> identifierResolver,
            boolean managedEntity
    ) {
        this.stateManager = requireNonNull(stateManager, "The StateManager is required");
        this.type = requireNonNull(type, "The type is required");
        this.identifierResolver = requireNonNull(identifierResolver, "The ModelIdentifierResolver is required");
        this.managedEntity = managedEntity;
    }

    @Override
    public Object resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
        Object resolvedId = identifierResolver.resolve(message, processingContext);
        //noinspection ConstantValue Users can still make the mistake to return null.
        if (resolvedId == null) {
            throw new NullEntityIdInPayloadException(message.getPayload().getClass());
        }
        if (managedEntity) {
            return stateManager.loadManagedEntity(type, resolvedId, processingContext).join();
        }
        return stateManager.loadEntity(type, resolvedId, processingContext).join();
    }

    @Override
    public boolean matches(Message<?> message, ProcessingContext processingContext) {
        return true;
    }
}
