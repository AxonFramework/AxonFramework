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

package org.axonframework.common.configuration;

import org.axonframework.common.AxonConfigurationException;

import java.util.function.UnaryOperator;

/**
 * A configurer that supports modifying {@link ConfigurationExtension} instances.
 * <p>
 * Extensions are created on first access and cached. The {@link #extend(Class, UnaryOperator)}
 * method configures an extension and returns {@code this} for fluent chaining.
 * <p>
 * For reading extensions, see {@link ExtendedConfiguration}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see ExtendedConfiguration
 */
public interface ExtensibleConfigurer {

    /**
     * Configures the extension of the given extensionType and returns {@code this} configurer for chaining.
     * <p>
     * The extension is created on first access and cached. The {@code customization} operator
     * is applied to the extension.
     * <p>
     * Example:
     * <pre>{@code
     * config.extend(DeadLetterQueueConfiguration.class, dlq -> dlq.enabled().factory(myFactory))
     *       .extend(MetricsExtension.class, m -> m.enabled());
     * }</pre>
     *
     * @param extensionType          the extension class
     * @param customization a function that configures the extension
     * @param <T>           the extension extensionType
     * @return {@code this} configurer, for fluent chaining
     * @throws AxonConfigurationException if the extension cannot be created
     */
    <T extends ConfigurationExtension<?>> ExtensibleConfigurer extend(Class<T> extensionType,
                                                                      UnaryOperator<T> customization);
}
