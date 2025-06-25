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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ComponentOverrideException;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Components;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.configuration.DuplicateModuleRegistrationException;
import org.axonframework.configuration.HierarchicalConfiguration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.OverridePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.ResolvableType;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A {@link ComponentRegistry} implementation that connects into Spring's ecosystem by means of being a
 * {@link BeanPostProcessor}, {@link BeanFactoryPostProcessor}, and {@link InitializingBean}.
 * <p>
 * By being a {@code BeanPostProcessor}, this {@code ComponentRegistry} can decorate any Spring bean that matches with
 * decorators set in this {@code ComponentRegistry} or any {@link ConfigurationEnhancer}.
 * <p>
 * By being a {@code BeanFactoryPostProcessor}, this {@code ComponentRegistry} can return any component, regardless of
 * whether it was registered with this {@code ComponentRegistry}, through a {@code ConfigurationEnhancer}, or comes from
 * Spring's Application Context directly. The latter integration ensures that <b>any</b> Axon Framework component using
 * the {@link Configuration} resulting from this {@code ComponentRegistry} can retrieve <b>any</b> bean that's
 * available. The {@link BeanFactory} that's set through
 * {@link BeanFactoryPostProcessor#postProcessBeanFactory(ConfigurableListableBeanFactory)} is also used to
 * {@link #hasComponent(Class, String) validate if th is registery has a certain component}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 4.6.0
 */
@Internal
public class SpringComponentRegistry implements
        BeanPostProcessor,
        BeanFactoryPostProcessor,
        ComponentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Components components = new Components();
    private final List<DecoratorDefinition.CompletedDecoratorDefinition<?, ?>> decorators = new CopyOnWriteArrayList<>();
    private final List<ConfigurationEnhancer> enhancers = new CopyOnWriteArrayList<>();
    private final Map<String, Module> modules = new ConcurrentHashMap<>();
    private final List<ComponentFactory<?>> factories = new ArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Configuration configuration = new SpringConfiguration();
    private final Map<String, Configuration> moduleConfigurations = new ConcurrentHashMap<>();

    private boolean enhancerScanning = true;
    private final List<Class<? extends ConfigurationEnhancer>> disabledEnhancers = new CopyOnWriteArrayList<>();

    private ConfigurableListableBeanFactory beanFactory;

    /**
     * Constructs a {@code SpringComponentRegistry} with the given {@code listableBeanFactory}. The
     * {@code listableBeanFactory} is used to discover all beans of type {@link ConfigurationEnhancer}.
     *
     * @param listableBeanFactory The bean factory used to discover all beans of type {@link ConfigurationEnhancer}.
     */
    @Internal
    public SpringComponentRegistry(@Nonnull ListableBeanFactory listableBeanFactory) {
        Objects.requireNonNull(listableBeanFactory, "The listableBeanFactory may not be null.");
        this.enhancers.addAll(listableBeanFactory.getBeansOfType(ConfigurationEnhancer.class).values());
    }

    @Override
    public <C> ComponentRegistry registerComponent(@Nonnull ComponentDefinition<? extends C> definition) {
        if (!(definition instanceof ComponentDefinition.ComponentCreator<? extends C> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + definition);
        }

        // We need to buffer these components, because they may depend on components that aren't
        // registered yet. We will register these in the application context just-in-time.
        Component<? extends C> component = creator.createComponent();
        if (components.contains(component.identifier())) {
            throw new ComponentOverrideException(creator.type(), creator.name());
        }

        components.put(component);
        return this;
    }

    @Override
    public <C> ComponentRegistry registerDecorator(@Nonnull DecoratorDefinition<C, ? extends C> definition) {
        if (!(definition instanceof DecoratorDefinition.CompletedDecoratorDefinition<C, ? extends C> decoratorRegistration)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported decorator definition type: " + definition);
        }

        logger.debug("Registering decorator definition: [{}]", definition);
        this.decorators.add(decoratorRegistration);
        return this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type) {
        // Checks both the local Components as the BeanFactory,
        //  since the ConfigurationEnhancers act before component registration with the Application Context.
        return components.contains(new Component.Identifier<>(type, null))
                || beanFactory.getBeanNamesForType(type).length > 0;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nullable String name) {
        Assert.notNull(name, () -> "Spring does not allow the use of null names for components.");
        // Checks both the local Components as the BeanFactory,
        //  since the ConfigurationEnhancers act before component registration with the Application Context.
        return components.contains(new Component.Identifier<>(type, name)) ||
                Arrays.stream(beanFactory.getBeanNamesForType(type)).toList().contains(name);
    }

    @Override
    public ComponentRegistry registerEnhancer(@Nonnull ConfigurationEnhancer enhancer) {
        logger.debug("Registering enhancer [{}].", enhancer.getClass().getSimpleName());
        this.enhancers.add(enhancer);
        return this;
    }

    @Override
    public ComponentRegistry registerModule(@Nonnull Module module) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering module [{}].", module.name());
        }
        if (modules.containsKey(module.name())) {
            throw new DuplicateModuleRegistrationException(module);
        }
        this.modules.put(module.name(), module);
        return this;
    }

    @Override
    public <C> ComponentRegistry registerFactory(@Nonnull ComponentFactory<C> factory) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering component factory [{}].", factory.getClass().getSimpleName());
        }
        // TODO #3075 Steven: Use ComponentFactories correctly
        this.factories.add(factory);
        return this;
    }

    @Override
    public ComponentRegistry setOverridePolicy(@Nonnull OverridePolicy overridePolicy) {
        if (overridePolicy != OverridePolicy.REJECT) {
            throw new IllegalArgumentException("Only OverridePolicy.REJECT is allowed when using Spring.");
        }
        return this;
    }

    @Override
    public ComponentRegistry disableEnhancerScanning() {
        this.enhancerScanning = true;
        return this;
    }

    @Override
    public ComponentRegistry disableEnhancer(Class<? extends ConfigurationEnhancer> enhancerClass) {
        disabledEnhancers.add(enhancerClass);
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialized", initialized.get());
        descriptor.describeProperty("components", components);
        descriptor.describeProperty("decorators", decorators);
        descriptor.describeProperty("configurerEnhancers", enhancers);
        descriptor.describeProperty("modules", modules.values());
        descriptor.describeProperty("factories", factories);
    }

    /**
     * Override from the {@link BeanPostProcessor} interface.
     * <p>
     * This ensures that {@link #registerDecorator(DecoratorDefinition) registered decorators} or decorators registered
     * through {@link ConfigurationEnhancer ConfigurationEnhancers} are invoked for Spring beans that match Axon's
     * type-and-name criteria for decoration.
     * <p>
     * Generic type checks on the {@link DecoratorDefinition} and it's invocations are suppressed as we're dealing with
     * wildcards. Furthermore, the
     * {@link DecoratorDefinition.CompletedDecoratorDefinition#matches(Component.Identifier)} invocation ensures we
     * validate if the {@link DecoratorDefinition.CompletedDecoratorDefinition#decorate(Component)} invocations is
     * valid.
     */
    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean,
                                                 @Nonnull String beanName) throws BeansException {
        Component<?> springComponent = new SpringComponent<>(bean, beanName);
        Component.Identifier<?> componentId = new Component.Identifier<>(bean.getClass(), beanName);

        //noinspection rawtypes
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decorators) {
            //noinspection unchecked
            if (decorator.matches(componentId)) {
                //noinspection unchecked
                springComponent = decorator.decorate(springComponent);
                break;
            }
        }

        return springComponent.resolve(configuration);
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * Accessor method for the {@link SpringAxonApplication} to access this registry's {@link Configuration}.
     *
     * @return The {@code Configuration} constructed by this {@link ComponentRegistry}.
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * This method does the following steps in order:
     * <ol>
     *     <li>Look for additional {@link ConfigurationEnhancer ConfigurationEnhancers} and {@link #registerEnhancer(ConfigurationEnhancer) registers} them.</li>
     *     <li>Invoke {@link ConfigurationEnhancer#enhance(ComponentRegistry)} on all registered enhancers.</li>
     *     <li>Registers <b>all</b> {@link Component Components} with the Application Context.</li>
     *     <li>Looks for any {@link Module Modules} and {@link #registerModule(Module) registers} them.</li>
     *     <li>Builds all registered {@code Modules} so that they become available to {@link Configuration#getModuleConfigurations()}.</li>
     * </ol>
     */
    void initialize(LifecycleRegistry lifecycleRegistry) {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("Component registry has already been initialized.");
        }
        scanForConfigurationEnhancers();
        invokeEnhancers();
        registerComponentsWithApplicationContext();
        scanForModules();
        buildModules(lifecycleRegistry);
        /*

        initializeComponents(config, lifecycleRegistry);
        registerFactoryShutdownHandlers(lifecycleRegistry);
        * */
    }

    /**
     * Scans for additional {@link ConfigurationEnhancer ConfigurationEnhancers} through means of a
     * {@link ServiceLoader}.
     * <p>
     * If {@link #disabledEnhancers disabled}, no {@code ServiceLoader} will be invoked.
     */
    private void scanForConfigurationEnhancers() {
        if (!enhancerScanning) {
            return;
        }
        ServiceLoader<ConfigurationEnhancer> enhancerLoader =
                ServiceLoader.load(ConfigurationEnhancer.class, getClass().getClassLoader());
        enhancerLoader.stream()
                      .map(ServiceLoader.Provider::get)
                      .filter(enhancer -> !disabledEnhancers.contains(enhancer.getClass()))
                      .forEach(this::registerEnhancer);
    }

    /**
     * Invoke all the {@link #registerEnhancer(ConfigurationEnhancer) registered}
     * {@link ConfigurationEnhancer enhancers} on this {@code ComponentRegistry} implementation in their
     * {@link ConfigurationEnhancer#order()}.
     * <p>
     * This will ensure all sensible default components and decorators are in place from these enhancers.
     * <p>
     * The disabledEnhancers filter is invoked in a for-loop instead of as a Stream operation, as a
     * {@code ConfigurationEnhancer} can add more enhancers that should be disabled. By making the filter part of the
     * stream operation, that update is lost.
     */
    private void invokeEnhancers() {
        List<ConfigurationEnhancer>
                distinctAndOrderedEnhancers = enhancers.stream()
                                                       .distinct()
                                                       .sorted(Comparator.comparingInt(ConfigurationEnhancer::order))
                                                       .toList();
        for (ConfigurationEnhancer enhancer : distinctAndOrderedEnhancers) {
            if (!disabledEnhancers.contains(enhancer.getClass())) {
                enhancer.enhance(this);
            }
        }
    }

    /**
     * Registers all {@link Component Components} that are present in the {@link Components} collection with the Spring
     * Application Context.
     * <p>
     * Each {@code Component} becomes a
     * {@link BeanDefinitionBuilder#rootBeanDefinition(ResolvableType, Supplier) root bean definition}, as that allows
     * us to use the {@link ResolvableType}. This is mandatory as we can otherwise not invoke the
     * {@link Component#resolve(Configuration)} operations as the factory method for the bean (Spring otherwise assumes
     * the given {@link Class} has the factory method named {@code "resolve"}).
     * <p>
     * The registration of {@code Components} should occur <b>after</b> all
     * {@link ConfigurationEnhancer ConfigurationEnhancers} have enhanced the configuration. By doing so, we ensure that
     * any defaults or overrides are present in the Application Context too.
     */
    private void registerComponentsWithApplicationContext() {
        components.postProcessComponents(component -> {
            String name = ObjectUtils.getOrDefault(component.identifier().name(),
                                                   () -> component.identifier().type().getName());
            if (beanFactory.containsBeanDefinition(name)) {
                logger.info("Component with name [{}] is already available. Skipping registration.", name);
                return;
            }

            AbstractBeanDefinition definition =
                    BeanDefinitionBuilder.rootBeanDefinition(
                                                 ResolvableType.forRawClass(component.identifier().type()),
                                                 () -> component.resolve(configuration)
                                         )
                                         .getBeanDefinition();
            ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(name, definition);
        });
    }

    /**
     * Look for all beans of type {@link Module} in the {@link ConfigurableListableBeanFactory} set by the
     * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory)} method and
     * {@link #registerModule(Module) registers} them.
     */
    private void scanForModules() {
        beanFactory.getBeansOfType(Module.class)
                   .forEach((beanName, module) -> registerModule(module));
    }

    /**
     * Ensure all registered {@link Module Modules} are built too. Store their {@link Configuration} results for
     * exposure on {@link Configuration#getModuleConfigurations()}.
     */
    private void buildModules(LifecycleRegistry lifecycleRegistry) {
        for (Module module : modules.values()) {
            Configuration builtModule = HierarchicalConfiguration.build(
                    lifecycleRegistry, (childLifecycleRegistry) -> module.build(configuration, childLifecycleRegistry)
            );
            moduleConfigurations.put(module.name(), builtModule);
        }
    }

    private class SpringConfiguration implements Configuration {

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type) {
            return beanFactory.getBean(type);
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nullable String name) {
            Assert.notNull(name, () -> "Spring does not allow the use of null names for component retrieval.");
            //noinspection DataFlowIssue
            return beanFactory.getBean(name, type);
        }

        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type) {
            return Optional.ofNullable(beanFactory.getBeanProvider(type).getIfAvailable());
        }

        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nullable String name) {
            Map<String, C> beansOfType = beanFactory.getBeansOfType(type);
            if (beansOfType.containsKey(name)) {
                return Optional.of(beansOfType.get(name));
            } else {
                return Optional.empty();
            }
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nullable String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            return getOptionalComponent(type, name).orElseGet(defaultImpl);
        }

        @Override
        public List<Configuration> getModuleConfigurations() {
            return List.copyOf(moduleConfigurations.values());
        }

        @Override
        public Optional<Configuration> getModuleConfiguration(@Nonnull String name) {
            return Optional.ofNullable(moduleConfigurations.get(name));
        }

        @Nullable
        @Override
        public Configuration getParent() {
            BeanFactory parentBeanFactory = beanFactory.getParentBeanFactory();
            if (parentBeanFactory != null) {
                return parentBeanFactory.getBean(Configuration.class);
            }
            return null;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("components", components);
            descriptor.describeProperty("modules", moduleConfigurations.values());
        }
    }

    private static final class SpringComponent<T> implements Component<T> {

        private final Identifier<T> identifier;
        private final T bean;

        private SpringComponent(@Nonnull T bean, @Nonnull String beanName) {
            //noinspection unchecked
            this.identifier = new Identifier<>((Class<T>) bean.getClass(), beanName);
            this.bean = bean;
        }

        @Override
        public Identifier<T> identifier() {
            return identifier;
        }

        @Override
        public T resolve(@Nonnull Configuration configuration) {
            return bean;
        }

        @Override
        public boolean isInstantiated() {
            return true;
        }

        @Override
        public void initLifecycle(@Nonnull Configuration configuration,
                                  @Nonnull LifecycleRegistry lifecycleRegistry) {
            // Unimplemented since Spring manages the lifecycle of all beans.
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("identifier", identifier);
            descriptor.describeProperty("bean", bean);
        }
    }
}
