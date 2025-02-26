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
import org.axonframework.modelling.command.ModelContainer;
import org.axonframework.modelling.command.ModelRegistry;

/**
 * {@link ParameterResolver} implementation that resolves a {@link ModelContainer} from the {@link ModelRegistry} in the
 * {@link ProcessingContext}.
 *
 * @author Mitchell Herrijgers
 * @see ModelRegistry
 * @since 5.0.0
 */
class ModelContainerParameterResolver implements ParameterResolver<ModelContainer> {

    private final ModelRegistry modelRegistry;

    /**
     * Instantiate a {@link ParameterResolver} that resolves a {@link ModelContainer} for the given
     * {@code modelRegistry}.
     *
     * @param modelRegistry The {@link ModelRegistry} to resolve the model from
     */
    public ModelContainerParameterResolver(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public ModelContainer resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
        return modelRegistry.modelContainer(processingContext);
    }

    @Override
    public boolean matches(Message<?> message, ProcessingContext processingContext) {
        return true;
    }
}
