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

package org.axonframework.modelling.command;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.ModelRegistry;

import javax.annotation.Nullable;

/**
 * Resolver for the identifier of a model. The identifier is used to identify the model in the {@link ModelRegistry}.
 *
 * @param <I> The type of the identifier.
 * @author Mitchell Herrijgers
 * @see ModelRegistry
 * @since 5.0.0
 */
@FunctionalInterface
public interface ModelIdentifierResolver<I> {

    /**
     * Resolve the identifier of the model from the given {@code message} and {@code context}.
     *
     * @param message The message to resolve the identifier from.
     * @param context The context in which the message is processed.
     * @return The identifier of the model.
     */
    @Nullable
    I resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context);
}
