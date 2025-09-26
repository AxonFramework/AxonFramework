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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link ComponentRegistry} allowing for reuse of {@link Component},
 * {@link ComponentDecorator}, {@link ConfigurationEnhancer}, and {@link Module} registration for the
 * {@link ApplicationConfigurer} and {@link Module} implementations alike.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DefaultComponentRegistry implements ComponentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Components components = new Components();
    private final List<DecoratorDefinition.CompletedDecoratorDefinition<?, ?>> decoratorDefinitions = new ArrayList<>();
    private final Map<String, ConfigurationEnhancer> enhancers = new LinkedHashMap<>();
    private final Map<String, Module> modules = new ConcurrentHashMap<>();
    private final List<ComponentFactory<?>> factories = new ArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, Configuration> moduleConfigurations = new ConcurrentHashMap<>();

    private OverridePolicy overridePolicy = OverridePolicy.WARN;
    private boolean enhancerScanning = true;
    private final List<Class<? extends ConfigurationEnhancer>> disabledEnhancers = new ArrayList<>();
    private final List<Class<? extends ConfigurationEnhancer>> invokedEnhancers = new ArrayList<>();

    private Optional<Configuration> parentConfig = Optional.empty();

    @Override
    public <C> ComponentRegistry registerComponent(@Nonnull ComponentDefinition<? extends C> definition) {
        requireNonNull(definition, "The ComponentDefinition must not be null.");
        if (!(definition instanceof ComponentDefinition.ComponentCreator<? extends C> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + definition);
        }

        Component<? extends C> component = creator.createComponent();
        Identifier<? extends C> id = component.identifier();
        logger.debug("Registering component [{}] of type [{}].", id.name(), id.type());
        if (overridePolicy == OverridePolicy.REJECT && hasComponent(id.typeAsClass(), id.name())) {
            throw new ComponentOverrideException(id.typeAsClass(), id.name());
        }

        Component<? extends C> previous = components.put(component);
        if (previous != null && overridePolicy == OverridePolicy.WARN) {
            logger.warn("Replaced a previous Component registered for type [{}] and name [{}].",
                        id.name(),
                        id.type());
        }
        return this;
    }

    @Override
    public <C> ComponentRegistry registerDecorator(@Nonnull DecoratorDefinition<C, ? extends C> definition) {
        requireNonNull(definition, "The decorator definition must not be null.");
        if (!(definition instanceof DecoratorDefinition.CompletedDecoratorDefinition<C, ? extends C> decoratorRegistration)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported decorator definition type: " + definition);
        }

        logger.debug("Registering decorator definition: [{}]", definition);
        decoratorDefinitions.add(decoratorRegistration);
        return this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nullable String name,
                                @Nonnull SearchScope searchScope) {
        return switch (searchScope) {
            case ALL -> components.contains(new Identifier<>(type, name)) || parentHasComponent(type, name);
            case CURRENT -> components.contains(new Identifier<>(type, name));
            case ANCESTORS -> parentHasComponent(type, name);
        };
    }

    private Boolean parentHasComponent(Class<?> type, String name) {
        return parentConfig.map(parent -> parent.hasComponent(type, name)).orElse(false);
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

    /**
     * Builds the {@link Configuration} from this {@code ComponentRegistry} as a root configuration.
     * <p>
     * The given {@code lifecycleRegistry} is used to register components' lifecycle methods.
     *
     * @param lifecycleRegistry The registry where lifecycle handlers are registered.
     * @return A fully initialized configuration exposing all configured components.
     */
    public Configuration build(@Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(null, lifecycleRegistry);
    }

    /**
     * Builds the {@link Configuration} from this {@code ComponentRegistry} as a nested configuration under the given
     * {@code parent}.
     * <p>
     * Components registered in the {@code parent} are available to components registered in this registry, but not vice
     * versa. The given {@code lifecycleRegistry} is used to register components' lifecycle methods.
     *
     * @param parent            The parent configuration.
     * @param lifecycleRegistry The registry where lifecycle handlers are registered.
     * @return A fully initialized configuration exposing all configured components.
     */
    public Configuration buildNested(@Nonnull Configuration parent,
                                     @Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(requireNonNull(parent), requireNonNull(lifecycleRegistry));
    }

    private Configuration doBuild(@Nullable Configuration optionalParent,
                                  @Nonnull LifecycleRegistry lifecycleRegistry) {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("Component registry has already been initialized.");
        }
        this.parentConfig = Optional.ofNullable(optionalParent);
        if (enhancerScanning) {
            scanForConfigurationEnhancers();
        }
        invokeEnhancers();
        decorateComponents();
        Configuration config = new LocalConfiguration(optionalParent);
        buildModules(config, lifecycleRegistry);
        initializeComponents(config, lifecycleRegistry);
        registerFactoryShutdownHandlers(lifecycleRegistry);

        return config;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void decorateComponents() {
        decoratorDefinitions.sort(Comparator.comparingInt(DecoratorDefinition.CompletedDecoratorDefinition::order));
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decoratorDefinitions) {
            for (Identifier id : components.identifiers()) {
                if (decorator.matches(id)) {
                    components.replace(id, decorator::decorate);
                }
            }
        }
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
     */
    private void invokeEnhancers() {
        List<ConfigurationEnhancer>
                distinctAndOrderedEnhancers = enhancers.values()
                                                       .stream()
                                                       .distinct()
                                                       .sorted(Comparator.comparingInt(ConfigurationEnhancer::order))
                                                       .toList();
        for (ConfigurationEnhancer enhancer : distinctAndOrderedEnhancers) {
            if (!disabledEnhancers.contains(enhancer.getClass())) {
                enhancer.enhance(this);
                invokedEnhancers.add(enhancer.getClass());
            }
        }
    }

    /**
     * Ensure all registered {@link Module Modules} are built too. Store their {@link Configuration} results for
     * exposure on {@link Configuration#getModuleConfigurations()}.
     */
    private void buildModules(Configuration config, LifecycleRegistry lifecycleRegistry) {
        for (Module module : modules.values()) {
            var builtModule = HierarchicalConfiguration.build(
                    lifecycleRegistry, (childLifecycleRegistry) -> module.build(config, childLifecycleRegistry)
            );
            moduleConfigurations.put(module.name(), builtModule);
        }
    }

    /**
     * Initialize the components defined in this registry, allowing them to register their lifecycle actions with given
     * {@code lifecycleRegistry}.
     *
     * @param lifecycleRegistry The registry where components may register their lifecycle actions.
     */
    private void initializeComponents(Configuration config, LifecycleRegistry lifecycleRegistry) {
        components.postProcessComponents(c -> c.initLifecycle(config, lifecycleRegistry));
    }

    /**
     * Registers the shutdown handlers for all
     * {@link #registerFactory(ComponentFactory) registered ComponentFactories}.
     *
     * @param lifecycleRegistry The registry where {@link ComponentFactory ComponentFactories} may register their
     *                          shutdown operations.
     */
    private void registerFactoryShutdownHandlers(LifecycleRegistry lifecycleRegistry) {
        factories.forEach(factory -> factory.registerShutdownHandlers(lifecycleRegistry));
    }

    @Override
    public DefaultComponentRegistry setOverridePolicy(@Nonnull OverridePolicy overridePolicy) {
        this.overridePolicy = requireNonNull(overridePolicy, "The override policy must not be null.");
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
    public DefaultComponentRegistry disableEnhancer(Class<? extends ConfigurationEnhancer> enhancerClass) {
        if (invokedEnhancers.contains(enhancerClass)) {
            logger.warn("Disabling Configuration Enhancer [{}] won't take effect as it has already been invoked. "
                                + "We recommend to invoke disabling of this enhancer before it takes effect.",
                        enhancerClass.getSimpleName());
            return this;
        }
        if (logger.isInfoEnabled()) {
            logger.info(
                    "Configuration Enhancer [{}] has been disabled. "
                            + "Ensure components set by this enhancer are not mandatory in this application.",
                    enhancerClass
            );
        }
        this.disabledEnhancers.add(enhancerClass);
        return this;
    }

    @Override
    public DefaultComponentRegistry disableEnhancerScanning() {
        this.enhancerScanning = false;
        return this;
    }

    private void scanForConfigurationEnhancers() {
        ServiceLoader<ConfigurationEnhancer> enhancerLoader = ServiceLoader.load(
                ConfigurationEnhancer.class, getClass().getClassLoader()
        );
        enhancerLoader.stream()
                      .map(ServiceLoader.Provider::get)
                      .filter(enhancer -> !disabledEnhancers.contains(enhancer.getClass()))
                      .filter(this::isNotYetRegistered)
                      .forEach(this::registerEnhancer);
    }

    /**
     * Filter ensuring the {@link ConfigurationEnhancer} ServiceLoader solution does not add an enhancer that was
     * already set by a higher level Configurer.
     *
     * @param enhancer The enhancer to check if it is already present.
     * @return {@code true} if the given {@code enhancer} has not been registered yet, {@code false} otherwise.
     */
    private boolean isNotYetRegistered(ConfigurationEnhancer enhancer) {
        return !enhancers.containsKey(enhancer.getClass().getName());
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialized", initialized.get());
        descriptor.describeProperty("components", components);
        descriptor.describeProperty("decorators", decoratorDefinitions);
        descriptor.describeProperty("configurerEnhancers", enhancers);
        descriptor.describeProperty("modules", modules.values());
        descriptor.describeProperty("factories", factories);
    }

    private class LocalConfiguration implements Configuration {

        private final Configuration parent;

        /**
         * Construct a {@code LocalConfiguration} using the given {@code parent} configuration.
         * <p>
         * If this configuration does not have a certain {@link Component}, it will fall back to it's {@code parent}.
         * <p>
         * Note that the {@code parent} can be {@code null}.
         *
         * @param parent The parent life cycle supporting configuration to fall back on when necessary.
         */
        public LocalConfiguration(@Nullable Configuration parent) {
            this.parent = parent;
        }

        @Override
        public Configuration getParent() {
            return parent;
        }

        @Nonnull
        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nullable String name) {
            return components.get(new Identifier<>(type, name))
                             .map(c -> c.resolve(this))
                             .or(() -> {
                                 Optional<Component<C>> factoryComponent = fromFactory(type, name);
                                 if (factoryComponent.isPresent()) {
                                     components.put(factoryComponent.get());
                                     return factoryComponent.map(creator -> creator.resolve(this));
                                 }
                                 return Optional.empty();
                             })
                             .or(() -> Optional.ofNullable(fromParent(type, name, () -> null)));
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nullable String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            Identifier<C> identifier = new Identifier<>(type, name);
            Object component = components.computeIfAbsent(
                                                 identifier,
                                                 () -> fromFactory(type, name).orElseGet(
                                                         () -> new LazyInitializedComponentDefinition<>(
                                                                 identifier,
                                                                 c -> fromParent(type, name, defaultImpl)
                                                         )
                                                 )
                                         )
                                         .resolve(this);
            return type.cast(component);
        }

        private <C> Optional<Component<C>> fromFactory(Class<C> type, String name) {
            if (name == null) {
                // The ComponentFactory requires a non-null name at all times.
                return Optional.empty();
            }

            for (ComponentFactory<?> factory : factories) {
                if (!type.isAssignableFrom(factory.forType())) {
                    continue;
                }
                //noinspection unchecked - suppress ComponentFactory cast
                Optional<Component<C>> factoryComponent = ((ComponentFactory<C>) factory).construct(name, this);
                if (factoryComponent.isPresent()) {
                    return factoryComponent;
                }
            }
            return Optional.empty();
        }

        private <C> C fromParent(Class<C> type, String name, Supplier<C> defaultSupplier) {
            return parent != null
                    ? parent.getOptionalComponent(type, name).orElseGet(defaultSupplier)
                    : defaultSupplier.get();
        }

        @Override
        public List<Configuration> getModuleConfigurations() {
            return List.copyOf(moduleConfigurations.values());
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("components", components);
            descriptor.describeProperty("modules", moduleConfigurations.values());
        }


        @Override
        public Optional<Configuration> getModuleConfiguration(@Nonnull String name) {
            Assert.nonEmpty(name, "The name must not be null.");
            return Optional.ofNullable(moduleConfigurations.get(name));
        }
    }
}
