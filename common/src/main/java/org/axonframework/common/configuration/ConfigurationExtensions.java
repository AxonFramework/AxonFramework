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
import org.axonframework.common.ConstructorUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Internal helper that manages the lifecycle of {@link ConfigurationExtension} instances for an
 * {@link ExtensibleConfigurer} parent configuration.
 * <p>
 * Extensions are created lazily on first access via {@link #extend(Class, UnaryOperator)} and cached for subsequent
 * calls. The class uses reflection to find a single-argument constructor on the extension type whose parameter is
 * assignable from the parent configuration's type, ensuring type compatibility at creation time.
 * <p>
 * This class is not part of the public API. It exists purely to encapsulate extension management so that
 * {@link ExtensibleConfigurer} implementations can delegate to it without duplicating logic.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class ConfigurationExtensions implements DescribableComponent {

    private final ExtensibleConfigurer parentConfigurer;
    private final Map<Class<? extends ConfigurationExtension<?>>, ConfigurationExtension<?>> extensions =
            new LinkedHashMap<>();

    /**
     * Constructs a new {@code ConfigurationExtensions} for the given {@code parentConfigurer}.
     *
     * @param parentConfigurer the parent configuration that owns these extensions
     */
    public ConfigurationExtensions(ExtensibleConfigurer parentConfigurer) {
        Objects.requireNonNull(parentConfigurer, "Parent configuration may not be null");
        this.parentConfigurer = parentConfigurer;
    }

    /**
     * Returns the extension of the given {@code extensionType}, creating it on first access.
     * <p>
     * The extension is instantiated via its single-argument constructor whose parameter extensionType is assignable
     * from the parent configuration's class. If no such constructor exists, an {@link AxonConfigurationException} is
     * thrown.
     *
     * @param extensionType the extension class
     * @param <T>           the extension extensionType
     * @return the extension instance, never {@code null}
     * @throws AxonConfigurationException if the extension cannot be created
     */
    @SuppressWarnings("unchecked")
    public <T extends ConfigurationExtension<?>> T extension(Class<T> extensionType) {
        return (T) extensions.computeIfAbsent(extensionType, this::createExtension);
    }

    /**
     * Configures the extension of the given {@code type} using the {@code customization} operator and returns the
     * parent configurer for chaining.
     *
     * @param type          the extension class
     * @param customization a function that configures the extension
     * @param <T>           the extension type
     * @return the parent configurer, for fluent chaining
     * @throws AxonConfigurationException if the extension cannot be created
     */
    public <T extends ConfigurationExtension<?>> ExtensibleConfigurer extend(Class<T> type,
                                                                             UnaryOperator<T> customization) {
        T ext = extension(type);
        customization.apply(ext);
        return parentConfigurer;
    }

    /**
     * Validates all registered extensions by calling {@link ConfigurationExtension#validate()} on each.
     *
     * @throws AxonConfigurationException if any extension's validation fails
     */
    public void validate() {
        extensions.values().forEach(ConfigurationExtension::validate);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        Map<String, ConfigurationExtension<?>> namedExtensions = extensions.values()
                                                                           .stream()
                                                                           .collect(Collectors.toMap(
                                                                                   ConfigurationExtension::name,
                                                                                   Function.identity(),
                                                                                   (a, b) -> a,
                                                                                   LinkedHashMap::new
                                                                           ));
        descriptor.describeProperty("extensions", namedExtensions);
    }

    /**
     * Copies all extensions from {@code this} instance to the given {@code target}.
     * <p>
     * This is intended for use in copy constructors of {@link ExtendedConfiguration} implementations.
     *
     * @param target the target {@code ConfigurationExtensions} to copy extensions into
     */
    @Internal
    public void copyTo(ConfigurationExtensions target) {
        target.extensions.putAll(this.extensions);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfigurationExtension<?> createExtension(Class<? extends ConfigurationExtension<?>> type) {
        try {
            return (ConfigurationExtension<?>) ConstructorUtils.factoryForTypeWithOptionalArgument(
                    (Class) type, parentConfigurer.getClass()
            ).apply(parentConfigurer);
        } catch (IllegalArgumentException e) {
            throw new AxonConfigurationException(
                    "No compatible constructor found on [" + type.getName()
                            + "] for parent type [" + parentConfigurer.getClass().getName() + "]", e
            );
        }
    }
}
