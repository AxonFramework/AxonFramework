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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.ModelIdResolver;
import org.axonframework.modelling.ModelRegistry;

/**
 * {@link ParameterResolver} implementation that resolves a model from the {@link ModelRegistry} in the
 * {@link ProcessingContext}. The model is resolved based on the identifier resolved by the given
 * {@link ModelIdResolver}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
class InjectModelParameterResolver implements ParameterResolver<Object> {

    private final ModelRegistry registry;
    private final Class<?> type;
    private final ModelIdResolver<?> idResolver;

    /**
     * Instantiate a {@link ParameterResolver} that resolves a model for the given {@code registry}, {@code type} and
     * {@code idResolverClass}.
     *
     * @param registry   The {@link ModelRegistry} to resolve the model from
     * @param type       The type of the model to resolve
     * @param idResolver The {@link ModelIdResolver} to resolve the identifier of the model
     */
    public InjectModelParameterResolver(ModelRegistry registry, Class<?> type,
                                        ModelIdResolver<?> idResolver) {
        this.registry = registry;
        this.type = type;
        this.idResolver = idResolver;
    }

    @Override
    public Object resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
        Object resolvedId = idResolver.resolve(message, processingContext);
        if (resolvedId == null) {
            throw new IllegalStateException(
                    "The idResolver returned null, while it is expected to return a non-null value");
        }
        var container = registry.modelContainer(processingContext);
        return container.getModel(type, resolvedId).join();
    }

    @Override
    public boolean matches(Message<?> message, ProcessingContext processingContext) {
        return true;
    }
}
