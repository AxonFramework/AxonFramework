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

package org.axonframework.extension.spring.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.annotations.Internal;
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
import org.axonframework.configuration.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
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
 * {@link #hasComponent(Class, String) validate if this registery has a certain component}.
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

    private final SpringLifecycleRegistry lifecycleRegistry;

    private final Components components = new Components();
    private final List<DecoratorDefinition.CompletedDecoratorDefinition<?, ?>> decorators = new CopyOnWriteArrayList<>();
    private final Map<String, ConfigurationEnhancer> enhancers = new ConcurrentHashMap<>();
    private final Map<String, Module> modules = new ConcurrentHashMap<>();
    private final List<ComponentFactory<?>> factories = new ArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Configuration configuration = new SpringConfiguration();
    private final Map<String, Configuration> moduleConfigurations = new ConcurrentHashMap<>();

    private boolean disableEnhancerScanning = false;
    private final List<Class<? extends ConfigurationEnhancer>> disabledEnhancers = new CopyOnWriteArrayList<>();
    private final List<Class<? extends ConfigurationEnhancer>> invokedEnhancers = new CopyOnWriteArrayList<>();

    private ConfigurableListableBeanFactory beanFactory;

    /**
     * Constructs a {@code SpringComponentRegistry} with the given {@code listableBeanFactory}. The
     * {@code listableBeanFactory} is used to discover all beans of type {@link ConfigurationEnhancer}.
     *
     * @param listableBeanFactory The bean factory used to discover all beans of type {@link ConfigurationEnhancer}.
     * @param lifecycleRegistry   The {@link LifecycleRegistry} used to initializes
     *                            {@link #registerModule(Module) registered modules}.
     */
    @Internal
    public SpringComponentRegistry(@Nonnull ListableBeanFactory listableBeanFactory,
                                   @Nonnull SpringLifecycleRegistry lifecycleRegistry) {
        Objects.requireNonNull(listableBeanFactory, "The ListableBeanFactory may not be null.");
        this.enhancers.putAll(listableBeanFactory.getBeansOfType(ConfigurationEnhancer.class));
        this.lifecycleRegistry =
                Objects.requireNonNull(lifecycleRegistry, "The Lifecycle Registry may not be null.");
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
            throw new ComponentOverrideException(creator.rawType(), creator.name());
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
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nullable String name,
                                @Nonnull SearchScope searchScope) {
        // Checks both the local Components as the BeanFactory,
        //  since the ConfigurationEnhancers act before component registration with the Application Context.
        return switch (searchScope) {
            case ALL -> components.contains(new Component.Identifier<>(type, name)) || contextHasComponent(type, name);
            case CURRENT -> components.contains(new Component.Identifier<>(type, name));
            case ANCESTORS -> contextHasComponent(type, name);
        };
    }

    /**
     * If the given {@code name} is {@code null}, we check if there is <b>a</b> bean for the given {@code type}.
     * <p>
     * If the given {@code name} is <b>not</b> {@code null}, we validate if there is a bean of the given {@code type}
     * with that exact name.
     */
    private boolean contextHasComponent(Class<?> type, String name) {
        return name != null
                ? Arrays.stream(beanFactory.getBeanNamesForType(type)).toList().contains(name)
                : beanFactory.getBeanNamesForType(type).length > 0;
    }

    @Override
    public ComponentRegistry registerEnhancer(@Nonnull ConfigurationEnhancer enhancer) {
        logger.debug("Registering enhancer [{}].", enhancer.getClass().getSimpleName());
        ConfigurationEnhancer previous = this.enhancers.put(enhancer.getClass().getName(), enhancer);
        if (previous != null) {
            logger.warn("Duplicate Configuration Enhancer registration dedicated. Replaced enhancer of type [{}].",
                        enhancer.getClass().getSimpleName());
        }
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
        this.factories.add(factory);
        return this;
    }

    @Override
    public ComponentRegistry setOverridePolicy(@Nonnull OverridePolicy overridePolicy) {
        if (overridePolicy != OverridePolicy.REJECT) {
            logger.warn("Enabling Component overriding on a Spring-based ComponentRegistry is not supported. "
                                + "Please use Spring \"Bean Definition Overriding Property\" instead.");
        }
        return this;
    }

    @Override
    public ComponentRegistry disableEnhancerScanning() {
        this.disableEnhancerScanning = true;
        return this;
    }

    @Override
    public ComponentRegistry disableEnhancer(@Nonnull String fullyQualifiedClassName) {
        Objects.requireNonNull(fullyQualifiedClassName, "The fully qualified class name must not be null.");
        try {
            var enhancerClass = Class.forName(fullyQualifiedClassName);
            if (!ConfigurationEnhancer.class.isAssignableFrom(enhancerClass)) {
                throw new IllegalArgumentException(
                        String.format("Class %s is not a ConfigurationEnhancer", fullyQualifiedClassName)
                );
            }
            //noinspection unchecked
            return disableEnhancer((Class<? extends ConfigurationEnhancer>) enhancerClass);
        } catch (ClassNotFoundException e) {
            logger.warn("Disabling Configuration Enhancer [{}] won't take effect as the enhancer class could not be found.", fullyQualifiedClassName);
        }
        return this;
    }

    @Override
    public ComponentRegistry disableEnhancer(Class<? extends ConfigurationEnhancer> enhancerClass) {
        if (invokedEnhancers.contains(enhancerClass)) {
            logger.warn("Disabling Configuration Enhancer [{}] won't take effect as it has already been invoked. "
                                + "We recommend to invoke disabling of this enhancer before it takes effect.",
                        enhancerClass.getSimpleName());
            return this;
        }
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

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * Override from the {@link BeanPostProcessor} interface.
     * <p>
     * This ensures that {@link #registerDecorator(DecoratorDefinition) registered decorators} or decorators registered
     * through {@link ConfigurationEnhancer ConfigurationEnhancers} are invoked for Spring beans that match Axon's
     * type-and-name criteria for decoration.
     * <p>
     * Will retrieve a {@link ResolvableType} from the given {@code beanName} from the
     * {@link ConfigurableListableBeanFactory} set through
     * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory)} (if contained in said bean factory). Doing so
     * ensures we match decorators with the bean's registered type instead of the bean's concrete type.
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
        // Ensure this ComponentRegistry is fully initialized, as this may set additional components and decorators.
        initialize();
        if (!beanFactory.containsBeanDefinition(beanName)) {
            logger.debug("Will not post process bean with name [{}] since we cannot define a Component Identifier.",
                         beanName);
            return bean;
        }

        ResolvableType beanType = beanFactory.getBeanDefinition(beanName).getResolvableType();
        if (beanType == ResolvableType.NONE) {
            logger.debug("Will not post process bean with name [{}] since we cannot define a Component Identifier.",
                         beanName);
            return bean;
        }

        Component.Identifier<Object> componentId = new Component.Identifier<>(beanType.getType(), beanName);
        if (isLocalComponent(componentId)) {
            logger.debug("Will not post process component [{}] since Axon registered it directly.", componentId);
            return bean;
        }

        Component<?> springComponent = new SpringComponent<>(componentId, bean);
        //noinspection rawtypes
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decorators) {
            //noinspection unchecked
            if (decorator.matches(componentId)) {
                //noinspection unchecked
                springComponent = decorator.decorate(springComponent);
            }
        }

        return springComponent.resolve(configuration);
    }

    /**
     * Checks whether the given {@code beanComponentId} is a bean {@code this ComponentRegistry} registered as part of
     * the {@link #initialize()}.
     * <p>
     * If {@code true} we are dealing with a component "local" to {@code this ComponentRegistry}. Furthermore, that
     * means the registered {@link #registerDecorator(DecoratorDefinition) decorators} have already processed for this
     * component. As such, this check can ensure we do not accidentally decorate Axon Framework components multiple
     * times.
     *
     * @param beanComponentId The identifier based on the type and name of the bean given on
     *                        {@link #postProcessAfterInitialization(Object, String)}.
     * @return {@code true} if we are dealing with a component "local" to {@code this ComponentRegistry}, {@code false}
     * otherwise.
     */
    private boolean isLocalComponent(Component.Identifier<Object> beanComponentId) {
        if (components.contains(beanComponentId)) {
            return true;
        }
        if (beanComponentId.areTypeAndNameEqual()) {
            return components.contains(new Component.Identifier<>(beanComponentId.type(), null));
        }
        return false;
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
     *     <li>Looks for any {@link DecoratorDefinition DecoratorDefinitions} and {@link #registerDecorator(DecoratorDefinition) registers} them.</li>
     *     <li>Decorate all registered {@code Components} by invoking all {@link #registerDecorator(DecoratorDefinition) registered decorators}.</li>
     *     <li>Registers <b>all</b> {@link Component Components} with the Application Context.</li>
     *     <li>Looks for any {@link Module Modules} and {@link #registerModule(Module) registers} them.</li>
     *     <li>Builds all registered {@code Modules} so that they become available to {@link Configuration#getModuleConfigurations()}.</li>
     *     <li>Looks for any {@link ComponentFactory ComponentFactories} and {@link #registerFactory(ComponentFactory) registers} them.</li>
     *     <li>{@link ComponentFactory#registerShutdownHandlers(LifecycleRegistry) Registers} all {@code ComponentFactory} shutdown handlers.</li>
     * </ol>
     */
    void initialize() {
        if (initialized.getAndSet(true)) {
            return;
        }
        scanForConfigurationEnhancers();
        invokeEnhancers();
        scanForDecoratorDefinitions();
        decorateComponents();
        registerLocalComponentsWithApplicationContext();
        scanForModules();
        buildModules();
        scanForComponentFactories();
        registerFactoryShutdownHandlers();
    }

    /**
     * Scans for additional {@link ConfigurationEnhancer ConfigurationEnhancers} through means of a
     * {@link ServiceLoader}.
     * <p>
     * If {@link #disabledEnhancers disabled}, no {@code ServiceLoader} will be invoked.
     */
    private void scanForConfigurationEnhancers() {
        if (disableEnhancerScanning) {
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
     * The disabled enhancers filter is invoked in a for-loop instead of as a Stream operation, as a
     * {@code ConfigurationEnhancer} can add more enhancers that should be disabled. By making the filter part of the
     * stream operation, that update is lost.
     * <p>
     * This method supports dynamic enhancer registration - if an enhancer registers another enhancer during its
     * {@link ConfigurationEnhancer#enhance(ComponentRegistry)} call, the newly registered enhancer will be processed
     * in the correct order based on its {@link ConfigurationEnhancer#order()} value relative to all unprocessed
     * enhancers. Each enhancer is processed one at a time to ensure proper ordering when new enhancers are registered
     * dynamically.
     */
    private void invokeEnhancers() {
        Set<String> processedEnhancerKeys = new HashSet<>();

        while (processedEnhancerKeys.size() < enhancers.size()) {
            // Find the next unprocessed enhancer with the lowest order value
            Optional<Map.Entry<String, ConfigurationEnhancer>> nextEnhancer =
                enhancers.entrySet()
                        .stream()
                        .filter(entry -> !processedEnhancerKeys.contains(entry.getKey()))
                        .min(Comparator.comparingInt(entry -> entry.getValue().order()));

            if (nextEnhancer.isEmpty()) {
                break; // No more enhancers to process
            }

            Map.Entry<String, ConfigurationEnhancer> entry = nextEnhancer.get();
            String key = entry.getKey();
            ConfigurationEnhancer enhancer = entry.getValue();

            if (!disabledEnhancers.contains(enhancer.getClass())) {
                enhancer.enhance(this);
                invokedEnhancers.add(enhancer.getClass());
            }
            processedEnhancerKeys.add(key);
        }
    }

    /**
     * Look for all beans of type {@link DecoratorDefinition} in the {@link ConfigurableListableBeanFactory} set by the
     * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory)} method and
     * {@link #registerDecorator(DecoratorDefinition) registers} them.
     */
    private void scanForDecoratorDefinitions() {
        //noinspection unchecked
        beanFactory.getBeansOfType(DecoratorDefinition.class)
                   .forEach((beanName, decorator) -> registerDecorator(decorator));
    }

    /**
     * Decorate all components that have been {@link #registerComponent(ComponentDefinition) registered directly} or
     * registered through a {@link ConfigurationEnhancer}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void decorateComponents() {
        decorators.sort(Comparator.comparingInt(DecoratorDefinition.CompletedDecoratorDefinition::order));
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decorators) {
            for (Component.Identifier id : components.identifiers()) {
                if (decorator.matches(id)) {
                    components.replace(id, decorator::decorate);
                }
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
    private void registerLocalComponentsWithApplicationContext() {
        components.postProcessComponents(component -> {
            String name = Objects.requireNonNullElseGet(component.identifier().name(),
                                                        () -> component.identifier().typeAsClass().getName());
            if (beanFactory.containsBeanDefinition(name)) {
                logger.info("Component with name [{}] is already available. Skipping registration.", name);
                return;
            }

            AbstractBeanDefinition definition =
                    BeanDefinitionBuilder.rootBeanDefinition(
                                                 ResolvableType.forType(component.identifier().type().getType()),
                                                 () -> component.resolve(configuration)
                                         )
                                         .getBeanDefinition();
            ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(name, definition);
            // Initialize the components lifecycle handlers, by adapting them into SmartLifecycle beans through the SpringLifecycleRegistry.
            component.initLifecycle(configuration, lifecycleRegistry);
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
    private void buildModules() {
        for (Module module : modules.values()) {
            Configuration builtModule = HierarchicalConfiguration.build(
                    lifecycleRegistry, (childLifecycleRegistry) -> module.build(configuration, childLifecycleRegistry)
            );
            moduleConfigurations.put(module.name(), builtModule);
        }
    }

    /**
     * Look for all beans of type {@link ComponentFactory} in the {@link ConfigurableListableBeanFactory} set by the
     * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory)} method and
     * {@link #registerFactory(ComponentFactory) registers} them.
     */
    private void scanForComponentFactories() {
        //noinspection unchecked
        beanFactory.getBeansOfType(ComponentFactory.class)
                   .forEach((beanName, factory) -> registerFactory(factory));
    }

    /**
     * Registers the shutdown handlers for all
     * {@link #registerFactory(ComponentFactory) registered ComponentFactories}.
     */
    private void registerFactoryShutdownHandlers() {
        factories.forEach(factory -> factory.registerShutdownHandlers(lifecycleRegistry));
    }

    private class SpringConfiguration implements Configuration {

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type) {
            try {
                return beanFactory.getBean(type);
            } catch (NoUniqueBeanDefinitionException e) {
                // Let's try to find a bean with the exact name instead.
                C bean = beanFactory.getBeansOfType(type)
                                    .get(type.getName());
                if (bean == null) {
                    // We couldn't be smarter, let's propagate the NoUniqueBeanDefinitionException instead.
                    throw e;
                }
                return bean;
            }
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
            } else if (name == null && beansOfType.containsKey(type.getName())) {
                // When name is null and a bean name equals FQCN, it is a configured Axon component based on type only.
                return Optional.of(beansOfType.get(type.getName()));
            } else if (name == null) {
                // Spring requires a non-null name, so we divert to the name-less method if name equals null.
                return getOptionalComponent(type);
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

        private SpringComponent(@Nonnull Identifier<T> identifier, @Nonnull T bean) {
            this.identifier = identifier;
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
