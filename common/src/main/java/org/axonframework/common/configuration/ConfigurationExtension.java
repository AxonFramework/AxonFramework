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

import java.util.Objects;

/**
 * A modular extension of an {@link ExtensibleConfiguration}. Parameterized with
 * the parent configuration type {@code P} so that subclasses get full typed
 * access to the parent's API.
 * <p>
 * Extensions are pure data — they hold settings but carry no behavior.
 * Behavior that acts on extension data belongs in a {@link ConfigurationEnhancer}.
 * <p>
 * Subclasses must provide a single-argument constructor accepting their parent type.
 * The constructor parameter type doubles as the parent compatibility constraint —
 * {@link ExtensibleConfiguration#extend(Class)} validates compatibility by matching
 * the constructor parameter against the calling configuration's type.
 * <p>
 * Extensions can be chained: {@code config.extend(A.class).extend(B.class)} works
 * because {@link #extend(Class)} delegates to the parent's extension map.
 *
 * @param <P> The parent configuration type this extension is designed for.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public abstract class ConfigurationExtension<P extends ExtensibleConfiguration>
        implements ExtensibleConfiguration, DescribableComponent {

    /**
     * The parent configuration this extension belongs to.
     */
    protected final P parent;

    /**
     * Constructs the extension with its parent configuration.
     *
     * @param parent The parent configuration this extension belongs to.
     */
    protected ConfigurationExtension(P parent) {
        this.parent = Objects.requireNonNull(parent, "Parent configuration may not be null");
    }

    /**
     * Delegates to the parent's extension map, enabling chaining:
     * {@code config.extend(A.class).extend(B.class)}.
     */
    @Override
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return parent.extend(type);
    }

    /**
     * Validates this extension's settings.
     *
     * @throws AxonConfigurationException if any settings are invalid.
     */
    public abstract void validate() throws AxonConfigurationException;
}
