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

import java.util.Objects;

/**
 * Convenience base class for {@link ConfigurationExtension} implementations that need
 * typed access to the parent configuration.
 * <p>
 * Stores the parent as a {@code protected final} field so that subclasses can access
 * the parent's API without casting.
 *
 * @param <P> The parent configuration type this extension is designed for.
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see ConfigurationExtension
 */
public abstract class AbstractConfigurationExtension<P extends ExtensibleConfiguration>
        implements ConfigurationExtension<P> {

    /**
     * The parent configuration this extension belongs to.
     */
    protected final P parent;

    /**
     * Constructs the extension with its parent configuration.
     *
     * @param parent The parent configuration this extension belongs to.
     */
    protected AbstractConfigurationExtension(P parent) {
        this.parent = Objects.requireNonNull(parent, "Parent configuration may not be null");
    }
}
