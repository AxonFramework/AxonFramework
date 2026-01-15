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

/**
 * Utility methods for working with Spring {@link BeanDefinition}s.
 *
 * @author Allard Buijze
 * @since 5.0.2
 */
@Internal
class BeanDefinitionUtils {

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
    static String extractPackageName(@Nonnull BeanDefinition definition) {
        String className = resolveClassName(definition);

        if (className != null && className.contains(".")) {
            return className.substring(0, className.lastIndexOf('.'));
        }

        return "default";
    }

    /**
     * Resolves the fully qualified class name from a bean definition.
     * <p>
     * Attempts multiple strategies to obtain the class name, returning the first non-null value.
     *
     * @param definition The bean definition to resolve the class name from.
     * @return The fully qualified class name, or null if it cannot be determined.
     */
    private static String resolveClassName(BeanDefinition definition) {
        // Standard bean class name
        String className = definition.getBeanClassName();
        if (className != null) {
            return className;
        }

        // Try AbstractBeanDefinition
        if (definition instanceof AbstractBeanDefinition abstractBeanDefinition
                && abstractBeanDefinition.hasBeanClass()) {
            return abstractBeanDefinition.getBeanClass().getName();
        }

        // Try AnnotatedBeanDefinition
        if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            return annotatedBeanDefinition.getMetadata().getClassName();
        }

        return null;
    }
}
