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
 * A configuration that supports modular extensions via {@link #extend(Class)} and
 * {@link #extend(Class, UnaryOperator)}.
 * <p>
 * Extensions are created on first access and cached — subsequent calls to
 * {@code extend()} with the same type return the same instance. This enables
 * natural merging: defaults and per-instance overrides both mutate the same
 * extension object.
 * <p>
 * Two access patterns are supported:
 * <ul>
 *     <li>{@link #extend(Class, UnaryOperator)} — configures the extension and returns {@code this}
 *         for chaining: {@code config.extend(A.class, a -> a.foo()).extend(B.class, b -> b.bar())}</li>
 *     <li>{@link #extend(Class)} — returns the extension instance for reading its current state</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public interface ExtensibleConfiguration {

    /**
     * Configures the extension of the given type and returns {@code this} configuration for chaining.
     * <p>
     * The extension is created on first access via its single-argument constructor (receiving
     * {@code this} as the parent). The {@code customization} operator is applied to the extension.
     * <p>
     * Example:
     * <pre>{@code
     * config.extend(DeadLetterQueueConfiguration.class, dlq -> dlq.enabled().factory(myFactory))
     *       .extend(MetricsExtension.class, m -> m.enabled());
     * }</pre>
     *
     * @param type          The extension class.
     * @param customization A function that configures the extension.
     * @param <T>           The extension type.
     * @return {@code this} configuration, for fluent chaining.
     * @throws AxonConfigurationException if the extension cannot be created.
     */
    <T extends ConfigurationExtension<?>> ExtensibleConfiguration extend(Class<T> type,
                                                                         UnaryOperator<T> customization);

    /**
     * Returns the extension of the given type, creating it on first access.
     * <p>
     * The extension is created via its single-argument constructor, receiving
     * {@code this} as the parent. If no compatible constructor exists (i.e., the
     * extension's parent type is not assignable from this configuration's type),
     * an {@link AxonConfigurationException} is thrown.
     * <p>
     * Subsequent calls with the same type return the cached instance.
     *
     * @param type The extension class.
     * @param <T>  The extension type.
     * @return The extension instance.
     * @throws AxonConfigurationException if the extension cannot be created.
     */
    <T extends ConfigurationExtension<?>> T extend(Class<T> type);
}
