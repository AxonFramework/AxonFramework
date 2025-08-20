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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Factory for creating simple {@link UnitOfWork} instances.
 * This factory allows for the creation of {@link UnitOfWork} instances with a default configuration,
 * which can be customized using a provided function.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SimpleUnitOfWorkFactory implements UnitOfWorkFactory {

    private final UnaryOperator<UnitOfWork.Configuration> factoryCustomization;

    /**
     * Initializes a {@link SimpleUnitOfWorkFactory} with the default configuration. This constructor uses the default
     * configuration for creating {@link UnitOfWork} instances without any customizations.
     */
    public SimpleUnitOfWorkFactory() {
        this(c -> c);
    }

    /**
     * Initializes a {@link SimpleUnitOfWorkFactory} with the given customization function. Allows customizing the
     * default configuration used to create {@link UnitOfWork} instances by this factory.
     *
     * @param factoryCustomization The function to customize the {@link UnitOfWork.Configuration} used to create
     *                             {@link UnitOfWork} instances.
     */
    public SimpleUnitOfWorkFactory(@Nonnull UnaryOperator<UnitOfWork.Configuration> factoryCustomization) {
        Objects.requireNonNull(factoryCustomization, "The factoryCustomization may not be null");
        this.factoryCustomization = factoryCustomization;
    }

    @Nonnull
    @Override
    public UnitOfWork create(
            @Nonnull String identifier,
            @Nonnull UnaryOperator<UnitOfWork.Configuration> customization
    ) {
        var configuration = customization.apply(factoryCustomization.apply(UnitOfWork.Configuration.defaultValues()));
        return new UnitOfWork(identifier, configuration);
    }
}
