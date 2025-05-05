/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.spring;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.configuration.OverridePolicy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class SpringComponentRegistry
        implements Configuration, InitializingBean, ComponentRegistry, BeanPostProcessor, BeanFactoryPostProcessor {

    private final Map<Component.Identifier<?>, Component<?>> components = new ConcurrentHashMap<>();
    private final SpringLifecycleRegistry springLifecycleRegistry;
    private final List<Module> modules = new CopyOnWriteArrayList<>();
    private final List<ConfigurationEnhancer> configurationEnhancers = new CopyOnWriteArrayList<>();
    private final List<DecoratorDefinition.CompletedDecoratorDefinition<?, ?>> decorators = new CopyOnWriteArrayList<>();
    private ConfigurableListableBeanFactory beanFactory;

    public SpringComponentRegistry(SpringLifecycleRegistry springLifecycleRegistry,
                                   ListableBeanFactory appContext) {
        this.springLifecycleRegistry = springLifecycleRegistry;
        this.configurationEnhancers.addAll(appContext.getBeansOfType(ConfigurationEnhancer.class).values());
    }

    @Override
    public <C> ComponentRegistry registerComponent(@Nonnull ComponentDefinition<? extends C> componentDefinition) {
        if (componentDefinition instanceof ComponentDefinition.ComponentCreator<?> cd) {
            // we need to buffer these components, because they may depend on components that aren't
            // registered yet. We will register these in the application context just-in-time
            Component<?> component = cd.createComponent();
            Component<?> previous = components.put(component.identifier(), component);
            if (previous != null) {
                throw new IllegalStateException("Duplicate component: " + component.identifier());
            }
        }
        return this;
    }

    @Override
    public <C> ComponentRegistry registerDecorator(@Nonnull DecoratorDefinition<C, ? extends C> decoratorDefinition) {
        this.decorators.add((DecoratorDefinition.CompletedDecoratorDefinition<?, ?>) decoratorDefinition);
        return this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type) {
        return beanFactory.getBeanProvider(type).getIfUnique() != null;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type, @Nonnull String name) {
        return beanFactory.containsBean(name) && type.isInstance(beanFactory.getBean(name));
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Component<?> springComponent = new SpringComponent<>(beanName, bean);
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decorators) {
            for (Class<?> iFace : ClassUtils.getAllInterfaces(bean)) {
                if (decorator.matches(new Component.Identifier<>(iFace, beanName))) {
                    springComponent = decorator.decorate(springComponent);
                    break;
                }
            }
        }
        return springComponent.resolve(this);
    }

    @Nonnull
    @Override
    public <C> C getComponent(@Nonnull Class<C> type) {
        return beanFactory.getBean(type);
    }

    @Nonnull
    @Override
    public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name) {
        return beanFactory.getBean(name, type);
    }

    @Override
    public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type) {
        return Optional.ofNullable(beanFactory.getBeanProvider(type).getIfAvailable());
    }

    @Override
    public ComponentRegistry registerEnhancer(@Nonnull ConfigurationEnhancer enhancer) {
        this.configurationEnhancers.add(enhancer);
        return this;
    }

    @Override
    public ComponentRegistry registerModule(@Nonnull Module module) {
        this.modules.add(module);
        return this;
    }

    @Override
    public ComponentRegistry setOverridePolicy(OverridePolicy overridePolicy) {
        if (overridePolicy != OverridePolicy.REJECT) {
            throw new IllegalArgumentException("Only OverridePolicy.REJECT is allowed");
        }
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        // TODO - Describe components
    }

    public void initialize() {
        configurationEnhancers.forEach(eh -> eh.enhance(this));
        // TODO - Iterate over all components to register their instances in the app context
        // TODO - Detect all Module implementations
        // TODO - Iterate over all configurationEnhancers
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type, @Nonnull String name) {
        Map<String, C> beansOfType = beanFactory.getBeansOfType(type);
        if (beansOfType.containsKey(name)) {
            return Optional.of(beansOfType.get(name));
        } else {
            return Optional.empty();
        }
    }

    @Nonnull
    @Override
    public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name, @Nonnull Supplier<C> defaultImpl) {
        return getOptionalComponent(type, name).orElseGet(defaultImpl);
    }

    @Override
    public List<NewConfiguration> getModuleConfigurations() {
        // TODO - Detect all Module implementations
        return List.of();
    }

    @Override
    public Optional<NewConfiguration> getModuleConfiguration(@Nonnull String name) {
        // TODO - Detect all Module implementations
        return Optional.empty();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        configurationEnhancers.forEach(configurationEnhancer -> configurationEnhancer.enhance(this));
    }

    private class SpringComponent<T> implements Component<T> {

        private final String beanName;
        private final T bean;

        public SpringComponent(String beanName, T bean) {
            this.beanName = beanName;
            this.bean = bean;
        }

        @Override
        public Identifier<T> identifier() {
            return new Identifier<>((Class<T>) bean.getClass(), beanName);
        }

        @Override
        public T resolve(@Nonnull NewConfiguration configuration) {
            return bean;
        }

        @Override
        public boolean isInstantiated() {
            return true;
        }

        @Override
        public void initLifecycle(@Nonnull NewConfiguration configuration,
                                  @Nonnull LifecycleRegistry lifecycleRegistry) {

        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {

        }
    }
}
