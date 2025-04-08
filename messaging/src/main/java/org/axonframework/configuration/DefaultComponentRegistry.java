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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link ComponentRegistry} allowing for reuse of {@link Component},
 * {@link ComponentDecorator}, {@link ConfigurationEnhancer}, and {@link Module} registration for the
 * {@code NewConfigurer} and {@link Module} implementations alike.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DefaultComponentRegistry implements ComponentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Components components = new Components();
    private final List<DecoratorDefinition.CompletedDecoratorDefinition<?, ?>> decoratorDefinitions = new ArrayList<>();
    private final List<ConfigurationEnhancer> enhancers = new ArrayList<>();
    private final Map<String, Module> modules = new ConcurrentHashMap<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, NewConfiguration> moduleConfigurations = new ConcurrentHashMap<>();

    private OverridePolicy overridePolicy = OverridePolicy.WARN;

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
        if (overridePolicy == OverridePolicy.REJECT && hasComponent(id.type(), id.name())) {
            throw new ComponentOverrideException(id.type(), id.name());
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
    public <C> ComponentRegistry registerDecorator(@Nonnull DecoratorDefinition<C, ? extends C> decorator) {
        requireNonNull(decorator, "The decorator definition must not be null.");
        if (!(decorator instanceof DecoratorDefinition.CompletedDecoratorDefinition<C, ? extends C> decoratorRegistration)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported decorator definition type: " + decorator);
        }

        logger.debug("Registering decorator definition: [{}]", decorator);
        decoratorDefinitions.add(decoratorRegistration);
        return this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nonnull String name) {
        return components.contains(new Component.Identifier<>(type, name));
    }

    @Override
    public ComponentRegistry registerEnhancer(@Nonnull ConfigurationEnhancer enhancer) {
        logger.debug("Registering enhancer [{}].", enhancer.getClass().getSimpleName());
        this.enhancers.add(enhancer);
        return this;
    }

    @Override
    public ComponentRegistry registerModule(@Nonnull Module module) {
        logger.debug("Registering module [{}].", module.name());
        if (modules.containsKey(module.name())) {
            throw new DuplicateModuleRegistrationException(module);
        }
        this.modules.put(module.name(), module);
        return this;
    }

    /**
     * Builds the {@link NewConfiguration} from this {@code ComponentRegistry} as a root configuration.
     * <p>
     * The given {@code lifecycleRegistry} is used to register components' lifecycle methods.
     *
     * @param lifecycleRegistry The registry where lifecycle handlers are registered.
     * @return A fully initialized configuration exposing all configured components.
     */
    public NewConfiguration build(@Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(null, lifecycleRegistry);
    }

    /**
     * Builds the {@link NewConfiguration} from this {@code ComponentRegistry} as a nested configuration under the given
     * {@code parent}.
     * <p>
     * Components registered in the {@code parent} are available to components registered in this registry, but not vice
     * versa. The given {@code lifecycleRegistry} is used to register components' lifecycle methods.
     *
     * @param parent            The parent configuration.
     * @param lifecycleRegistry The registry where lifecycle handlers are registered.
     * @return A fully initialized configuration exposing all configured components.
     */
    public NewConfiguration buildNested(@Nonnull NewConfiguration parent,
                                        @Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(requireNonNull(parent), requireNonNull(lifecycleRegistry));
    }

    private NewConfiguration doBuild(@Nullable NewConfiguration optionalParent,
                                     @Nonnull LifecycleRegistry lifecycleRegistry) {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("Component registry has already been initialized.");
        }

        invokeEnhancers();
        decorateComponents();
        NewConfiguration config = new LocalConfiguration(optionalParent);
        buildModules(config, lifecycleRegistry);
        initializeComponents(config, lifecycleRegistry);

        return config;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void decorateComponents() {
        decoratorDefinitions.sort(Comparator.comparingInt(DecoratorDefinition.CompletedDecoratorDefinition::order));
        for (DecoratorDefinition.CompletedDecoratorDefinition decorator : decoratorDefinitions) {
            for (Identifier id : components.identifiers()) {
                if (decorator.matches(id)) {
                    components.replace(id, previous -> decorator.decorate(previous));
                }
            }
        }
    }

    /**
     * Invoke all the {@link #registerEnhancer(ConfigurationEnhancer) registered}
     * {@link ConfigurationEnhancer enhancers} on this {@code ComponentRegistry} implementation in their
     * {@link ConfigurationEnhancer#order()}. This will ensure all sensible default components and decorators are in
     * place from these enhancers.
     */
    private void invokeEnhancers() {
        enhancers.stream()
                 .sorted(Comparator.comparingInt(ConfigurationEnhancer::order))
                 .forEach(enhancer -> enhancer.enhance(this));
    }

    /**
     * Ensure all registered {@link Module Modules} are built too. Store their {@link NewConfiguration} results for
     * exposure on {@link NewConfiguration#getModuleConfigurations()}.
     */
    private void buildModules(NewConfiguration config, LifecycleRegistry lifecycleRegistry) {
        for (Module module : modules.values()) {
            moduleConfigurations.put(module.name(), module.build(config, lifecycleRegistry));
        }
    }

    /**
     * Initialize the components defined in this registry, allowing them to register their lifecycle actions with given
     * {@code lifecycleRegistry}.
     *
     * @param lifecycleRegistry The registry where components may register their lifecycle actions.
     */
    private void initializeComponents(NewConfiguration config, LifecycleRegistry lifecycleRegistry) {
        components.postProcessComponents(c -> c.initLifecycle(config, lifecycleRegistry));
    }

    @Override
    public DefaultComponentRegistry setOverridePolicy(@Nonnull OverridePolicy overridePolicy) {
        this.overridePolicy = requireNonNull(overridePolicy, "The override policy must not be null.");
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialized", initialized.get());
        descriptor.describeProperty("components", components);
        descriptor.describeProperty("decorators", decoratorDefinitions);
        descriptor.describeProperty("configurerEnhancers", enhancers);
        descriptor.describeProperty("modules", modules.values());
    }

    private class LocalConfiguration implements NewConfiguration {

        private final NewConfiguration parent;

        /**
         * Construct a {@code LocalConfiguration} using the given {@code parent} configuration.
         * <p>
         * If this configuration does not have a certain {@link Component}, it will fall back to it's {@code parent}.
         * <p>
         * Note that the {@code parent} can be {@code null}.
         *
         * @param parent The parent life cycle supporting configuration to fall back on when necessary.
         */
        public LocalConfiguration(@Nullable NewConfiguration parent) {
            this.parent = parent;
        }

        @Nonnull
        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nonnull String name) {
            return components.get(new Identifier<>(type, name))
                             .map(c -> c.resolve(this))
                             .or(() -> Optional.ofNullable(fromParent(type, name, () -> null)));
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nonnull String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            Identifier<C> identifier = new Identifier<>(type, name);
            Object component = components.computeIfAbsent(
                                                 identifier,
                                                 () -> new LazyInitializedComponentDefinition<>(
                                                         identifier, c -> fromParent(type, name, defaultImpl)
                                                 )
                                         )
                                         .resolve(this);
            return type.cast(component);
        }

        private <C> C fromParent(Class<C> type, String name, Supplier<C> defaultSupplier) {
            return parent != null
                    ? parent.getOptionalComponent(type, name).orElseGet(defaultSupplier)
                    : defaultSupplier.get();
        }

        @Override
        public List<NewConfiguration> getModuleConfigurations() {
            return List.copyOf(moduleConfigurations.values());
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("components", components);
            descriptor.describeProperty("modules", moduleConfigurations.values());
        }


        @Override
        public Optional<NewConfiguration> getModuleConfiguration(@Nonnull String name) {
            Assert.nonEmpty(name, "The name must not be null.");
            return Optional.ofNullable(moduleConfigurations.get(name));
        }
    }
}
