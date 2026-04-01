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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Internal helper that manages the lifecycle of {@link ConfigurationExtension} instances for an
 * {@link ExtensibleConfiguration} owner.
 * <p>
 * Extensions are created lazily on first access via {@link #extend(Class)} and cached for subsequent calls.
 * The class uses reflection to find a single-argument constructor on the extension type whose parameter is
 * assignable from the owner's type, ensuring type compatibility at creation time.
 * <p>
 * This class is not part of the public API. It exists purely to encapsulate extension management so that
 * {@link ExtensibleConfiguration} implementations can delegate to it without duplicating logic.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public class ConfigurationExtensions implements DescribableComponent {

    private final ExtensibleConfiguration owner;
    private final Map<Class<? extends ConfigurationExtension<?>>, ConfigurationExtension<?>> extensions =
            new LinkedHashMap<>();

    /**
     * Constructs a new {@code ConfigurationExtensions} for the given {@code owner}.
     *
     * @param owner The {@link ExtensibleConfiguration} that owns these extensions.
     */
    public ConfigurationExtensions(ExtensibleConfiguration owner) {
        this.owner = owner;
    }

    /**
     * Returns the extension of the given {@code type}, creating it on first access.
     * <p>
     * The extension is instantiated via its single-argument constructor whose parameter type is assignable from the
     * owner's class. If no such constructor exists, an {@link AxonConfigurationException} is thrown.
     *
     * @param type The extension class.
     * @param <T>  The extension type.
     * @return The extension instance, never {@code null}.
     * @throws AxonConfigurationException if the extension cannot be created.
     */
    @SuppressWarnings("unchecked")
    public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
        return (T) extensions.computeIfAbsent(type, this::createExtension);
    }

    /**
     * Configures the extension of the given {@code type} using the {@code customization} operator
     * and returns the owner configuration for chaining.
     *
     * @param type          The extension class.
     * @param customization A function that configures the extension.
     * @param <T>           The extension type.
     * @return The owner configuration, for fluent chaining.
     * @throws AxonConfigurationException if the extension cannot be created.
     */
    public <T extends ConfigurationExtension<?>> ExtensibleConfiguration extend(Class<T> type,
                                                                                UnaryOperator<T> customization) {
        T extension = extend(type);
        customization.apply(extension);
        return owner;
    }

    /**
     * Validates all registered extensions by calling {@link ConfigurationExtension#validate()} on each.
     *
     * @throws AxonConfigurationException if any extension's validation fails.
     */
    public void validate() {
        extensions.values().forEach(ConfigurationExtension::validate);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        extensions.values().forEach(extension -> extension.describeTo(descriptor));
    }

    /**
     * Copies all extensions from {@code this} instance to the given {@code target}.
     * <p>
     * This is intended for use in copy constructors of {@link ExtensibleConfiguration} implementations.
     *
     * @param target The target {@code ConfigurationExtensions} to copy extensions into.
     */
    @Internal
    public void copyTo(ConfigurationExtensions target) {
        target.extensions.putAll(this.extensions);
    }

    private ConfigurationExtension<?> createExtension(Class<? extends ConfigurationExtension<?>> type) {
        Constructor<?> compatibleConstructor = Arrays.stream(type.getDeclaredConstructors())
                                                     .filter(c -> c.getParameterCount() == 1)
                                                     .filter(c -> c.getParameterTypes()[0].isAssignableFrom(
                                                             owner.getClass()))
                                                     .findFirst()
                                                     .orElseThrow(() -> new AxonConfigurationException(
                                                             "No compatible single-argument constructor found on ["
                                                                     + type.getName()
                                                                     + "] for owner type ["
                                                                     + owner.getClass().getName() + "]"
                                                     ));
        try {
            compatibleConstructor.setAccessible(true);
            return type.cast(compatibleConstructor.newInstance(owner));
        } catch (AxonConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new AxonConfigurationException(
                    "Failed to create extension [" + type.getName() + "]", e
            );
        }
    }
}
