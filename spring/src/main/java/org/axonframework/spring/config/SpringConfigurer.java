/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;
import java.util.Optional;

/**
 * A {@link org.axonframework.config.Configurer} implementation that considers the Spring Application context as a
 * source for components.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringConfigurer extends DefaultConfigurer {

    private final ComponentLocator locator;

    /**
     * Initialize this {@link org.axonframework.config.Configurer} using given {@code beanFactory} to locate
     * components.
     *
     * @param beanFactory The Bean Factory to find components in.
     */
    public SpringConfigurer(ConfigurableListableBeanFactory beanFactory) {
        locator = new ComponentLocator(beanFactory);
    }

    @Override
    protected <T> Optional<T> defaultComponent(Class<T> type, Configuration config) {
        return locator.findBean(type);
    }

    private static class ComponentLocator {

        private final ConfigurableListableBeanFactory beanFactory;

        public ComponentLocator(ConfigurableListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        public <T> Optional<T> findBean(Class<T> componentType) {
            String[] candidates = beanFactory.getBeanNamesForType(componentType);

            if (candidates.length == 0) {
                return Optional.empty();
            } else if (candidates.length == 1) {
                return Optional.of(beanFactory.getBean(candidates[0], componentType));
            } else {
                Optional<T> primary = findPrimary(componentType, candidates);
                if (!primary.isPresent()) {
                    throw new AxonConfigurationException("Expected single candidate for component [" + componentType.getSimpleName() + "]. Found candidates: " + Arrays.deepToString(candidates));
                }
                return primary;
            }
        }

        private <T> Optional<T> findPrimary(Class<T> componentType, String[] candidates) {
            String primary = null;
            for (String candidate : candidates) {
                if (beanFactory.getBeanDefinition(candidate).isPrimary()) {
                    if (primary != null) {
                        return Optional.empty();
                    } else {
                        primary = candidate;
                    }
                }
            }
            if (primary == null) {
                return Optional.empty();
            } else {
                return Optional.of(beanFactory.getBean(primary, componentType));
            }
        }
    }
}
