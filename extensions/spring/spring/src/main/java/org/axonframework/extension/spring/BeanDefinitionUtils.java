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

package org.axonframework.extension.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;

import java.util.Optional;

/**
 * Utility methods for working with Spring {@link BeanDefinition BeanDefinitions}.
 *
 * @author Allard Buijze
 * @since 5.0.2
 */
@Internal
public class BeanDefinitionUtils {

    private BeanDefinitionUtils() {
        // Utility class
    }

    /**
     * Extracts the package name from the given bean definition's class name.
     * <p>
     * This method attempts to determine the package name by examining the bean definition in the following order:
     * <ol>
     *     <li>The bean class name from {@link BeanDefinition#getBeanClassName()}</li>
     *     <li>For {@link AbstractBeanDefinition}, the bean class if available</li>
     *     <li>For {@link AnnotatedBeanDefinition}, the metadata class name</li>
     * </ol>
     * If a class name is found and contains a package (has a dot), returns the package portion. Otherwise, returns
     * "default".
     *
     * @param definition The bean definition to extract the package name from.
     * @return The package name, or "default" if the package cannot be determined.
     */
    @Nonnull
    public static String extractPackageName(@Nonnull BeanDefinition definition) {
        return resolveClassName(definition)
                .filter(className -> className.contains("."))
                .map(className -> className.substring(0, className.lastIndexOf('.')))
                .orElse("default");
    }

    /**
     * Resolves the fully qualified class name from a bean definition.
     * <p>
     * Attempts multiple strategies to obtain the class name, returning the first non-null value.
     *
     * @param definition The bean definition to resolve the class name from.
     * @return An Optional containing the fully qualified class name, or empty if it cannot be determined.
     */
    @Nonnull
    public static Optional<String> resolveClassName(@Nonnull BeanDefinition definition) {
        // Standard bean class name
        String className = definition.getBeanClassName();
        if (className != null) {
            return Optional.of(className);
        }

        // Try AbstractBeanDefinition
        if (definition instanceof AbstractBeanDefinition abstractBeanDefinition
                && abstractBeanDefinition.hasBeanClass()) {
            return Optional.of(abstractBeanDefinition.getBeanClass().getName());
        }

        // Try AnnotatedBeanDefinition
        if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            return Optional.of(annotatedBeanDefinition.getMetadata().getClassName());
        }

        return Optional.empty();
    }
}
