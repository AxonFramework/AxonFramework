/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.unitofwork;

import jakarta.annotation.Nonnull;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Factory for creating {@link UnitOfWork} instances. Useful to create units of work that are bound to some context,
 * such as a database transaction.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@FunctionalInterface
public interface UnitOfWorkFactory {

    /**
     * Creates a new {@link UnitOfWork} with a randomly generated identifier.
     *
     * @return A new {@link UnitOfWork} instance.
     */
    @Nonnull
    default UnitOfWork create() {
        return create(UUID.randomUUID().toString(), UnaryOperator.identity());
    }

    /**
     * Creates a new {@link UnitOfWork} with the given identifier.
     *
     * @param identifier The identifier for the unit of work.
     * @return A new {@link UnitOfWork} instance.
     */
    @Nonnull
    default UnitOfWork create(@Nonnull String identifier) {
        return create(identifier, UnaryOperator.identity());
    }

    /**
     * Creates a new {@link UnitOfWork} with the given identifier and applies the provided customization to its
     * configuration.
     *
     * @param identifier    The identifier for the unit of work.
     * @param customization A function to customize the unit of work's configuration.
     * @return A new {@link UnitOfWork} instance.
     */
    @Nonnull
    UnitOfWork create(@Nonnull String identifier,
                      @Nonnull Function<UnitOfWorkConfiguration, UnitOfWorkConfiguration> customization);

    /**
     * Creates a new {@link UnitOfWork} with a random identifier and applies the provided customization to its
     * configuration.
     *
     * @param customization A function to customize the unit of work's configuration.
     * @return A new {@link UnitOfWork} instance.
     */
    @Nonnull
    default UnitOfWork create(@Nonnull Function<UnitOfWorkConfiguration, UnitOfWorkConfiguration> customization) {
        return create(UUID.randomUUID().toString(), customization);
    }
}
