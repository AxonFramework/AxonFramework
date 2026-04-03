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
import org.axonframework.common.infra.DescribableComponent;

/**
 * A modular extension of an {@link ExtensibleConfiguration}. Parameterized with
 * the parent configuration type {@code P} so that implementations get full typed
 * access to the parent's API.
 * <p>
 * Extensions are pure data — they hold settings but carry no behavior.
 * Behavior that acts on extension data belongs in a {@link ConfigurationEnhancer}.
 * <p>
 * Implementations must provide a single-argument constructor accepting their parent type.
 * The constructor parameter type doubles as the parent compatibility constraint —
 * {@link ExtensibleConfiguration#extension(Class)} validates compatibility by matching
 * the constructor parameter against the calling configuration's type.
 * <p>
 * A convenience base class {@link AbstractConfigurationExtension} is provided that stores
 * the parent reference as a {@code protected final} field.
 *
 * @param <P> the parent configuration type this extension is designed for
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see AbstractConfigurationExtension
 */
public interface ConfigurationExtension<P extends ExtensibleConfiguration> extends DescribableComponent {

    /**
     * Returns the name of this configuration extension.
     *
     * @return the configuration extension's name
     */
    String name();

    /**
     * Validates this extension's settings.
     *
     * @throws AxonConfigurationException if any settings are invalid
     */
    void validate() throws AxonConfigurationException;
}
