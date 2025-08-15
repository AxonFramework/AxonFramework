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

import java.util.function.UnaryOperator;

/**
 * Factory for creating simple {@link UnitOfWork} instances. Create units of work by invoking the {@link UnitOfWork}
 * constructor.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SimpleUnitOfWorkFactory implements UnitOfWorkFactory {

    // todo: CustomizableUnitOfWorkFactory --- a few layers down... I can provide custom IdGenerator by mean of this!!!
    // todo: DecoratingUnitOfWorkFactory - here, just create() method! Maybe Decorating or Delegating???
    // identifier - IDK if makes sense in Configuration?

    private final UnaryOperator<UnitOfWork.Configuration> factoryCustomization; // customizers?
    //TODO: Configuration as a factory concern! and the Customizable part!?
    //Some of them will be Customizable some Decorating, and with many layers, for example in SimpleCommandBus i might just wrap it??? IDK?

    public SimpleUnitOfWorkFactory() {
        this(c -> c);
    }

    /**
     * Initializes a {@link SimpleUnitOfWorkFactory} with the given customization function. Allows customizing the
     * default configuration used to create {@link UnitOfWork} instances by this factory.
     *
     * @param factoryCustomization a function to customize the {@link UnitOfWork.Configuration} used to create
     *                             {@link UnitOfWork} instances.
     */
    public SimpleUnitOfWorkFactory(UnaryOperator<UnitOfWork.Configuration> factoryCustomization) {
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
