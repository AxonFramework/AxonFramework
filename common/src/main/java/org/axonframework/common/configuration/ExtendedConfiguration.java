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

/**
 * A configuration that supports reading modular {@link ConfigurationExtension} instances.
 * <p>
 * Extensions are created on first access and cached — subsequent calls to
 * {@code extension()} with the same type return the same instance.
 * <p>
 * For modifying extensions, see {@link ExtensibleConfigurer}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see ExtensibleConfigurer
 */
public interface ExtendedConfiguration {

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
     * @param type the extension class
     * @param <T>  the extension type
     * @return the extension instance
     * @throws AxonConfigurationException if the extension cannot be created
     */
    <T extends ConfigurationExtension<?>> T extension(Class<T> type);
}
