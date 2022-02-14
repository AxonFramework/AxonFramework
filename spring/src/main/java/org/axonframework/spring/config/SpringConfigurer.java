package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Optional;

/**
 * A Configurer implementation that considers the Spring Application context as a source for components
 */
public class SpringConfigurer extends DefaultConfigurer {

    private final ComponentLocator locator;

    /**
     * Initialize the SpringConfigurer using given {@code beanFactory} to locate components
     *
     * @param beanFactory The Bean Factory to find components in
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
                // find primary
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
}
