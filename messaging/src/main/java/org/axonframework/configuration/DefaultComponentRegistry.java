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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.Component.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Abstract implementation of the {@link ComponentRegistry} allowing for reuse of {@link Component},
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
    private final List<ConfigurationEnhancer> enhancers = new ArrayList<>();
    private final Map<String, Module> modules = new ConcurrentHashMap<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<DecoratorRegistration<?, ?>> decoratorRegistrations = new ArrayList<>();
    private final Map<String, NewConfiguration> moduleConfigurations = new ConcurrentHashMap<>();
    private NewConfiguration config;
    private OverrideBehavior overrideBehavior = OverrideBehavior.WARN;

    @Override
    public <C> ComponentRegistry registerComponent(@Nonnull Class<? extends C> type,
                                                   @Nonnull String name,
                                                   @Nonnull ComponentFactory<? extends C> factory) {
        logger.debug("Registering component [{}] of type [{}].", name, type);
        Identifier<C> identifier = new Identifier<>(type, name);
        if (overrideBehavior == OverrideBehavior.THROW && components.contains(identifier)) {
            throw new ComponentOverrideException(type, name);
        }

        Component<C> previous = components.put(new Component<>(identifier, factory));
        if (previous != null && overrideBehavior == OverrideBehavior.WARN) {
            logger.warn("Replaced a previous Component registered for type [{}] and name [{}].", name, type);
        }
        return this;
    }

    @Override
    public <C, D extends C> ComponentRegistry registerDecorator(@Nonnull Class<C> type,
                                                                int order,
                                                                @Nonnull ComponentDecorator<C, D> decorator) {
        Objects.requireNonNull(type, "The type must not be null");
        Objects.requireNonNull(decorator, "The component decorator must not be null");
        logger.debug("Registering decorator for type [{}] at order #{}.", type, order);
        decoratorRegistrations.add(new DecoratorRegistration<>(i -> i.type().equals(type), order, decorator));
        return this;
    }

    @Override
    public <C, D extends C> ComponentRegistry registerDecorator(@Nonnull Class<C> type, @Nonnull String name, int order,
                                                                @Nonnull ComponentDecorator<C, D> decorator) {
        Objects.requireNonNull(name, "name must not be null");
        logger.debug("Registering decorator for name [{}] and type [{}] at order #{}.", name, type, order);
        decoratorRegistrations.add(new DecoratorRegistration<>(
                i -> Objects.equals(i.name(), name) && Objects.equals(
                        i.type(),
                        type), order, decorator));
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
     * Builds the Configuration from this ComponentRegistry as a root configuration. The given {@code lifecycleRegistry}
     * is used to register Components' lifecycle methods.
     *
     * @param lifecycleRegistry The registry where lifecycle handlers are registered
     * @return a fully initialized Configuration exposing all configured Components
     */
    public NewConfiguration build(@Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(null, lifecycleRegistry);
    }

    /**
     * Builds the Configuration from this ComponentRegistry as a nested configuration under the given {@code parent}.
     * Components registered in the {@code parent} are available to components registered in this registry, but not vice
     * versa. The given {@code lifecycleRegistry} is used to register Components' lifecycle methods.
     *
     * @param parent            The parent Configuration
     * @param lifecycleRegistry The registry where lifecycle handlers are registered
     * @return a fully initialized Configuration exposing all configured Components
     */
    public NewConfiguration buildNested(@Nonnull NewConfiguration parent,
                                        @Nonnull LifecycleRegistry lifecycleRegistry) {
        return doBuild(Objects.requireNonNull(parent), Objects.requireNonNull(lifecycleRegistry));
    }

    private NewConfiguration doBuild(@Nullable NewConfiguration optionalParent,
                                     @Nonnull LifecycleRegistry lifecycleRegistry) {
        if (!initialized.getAndSet(true)) {
            this.config = new LocalConfiguration(optionalParent);
            invokeEnhancers();
            decorateComponents();
            buildModules(lifecycleRegistry);
            initializeComponents(lifecycleRegistry);
        }
        return this.config;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void decorateComponents() {
        decoratorRegistrations.sort(Comparator.comparingInt(DecoratorRegistration::order));
        for (DecoratorRegistration decoratorRegistration : decoratorRegistrations) {
            for (Identifier id : components.identifiers()) {
                if (decoratorRegistration.test(id)) {
                    components.replace(id, previous -> previous.decorate(decoratorRegistration.decorator));
                }
            }
        }
    }

    /**
     * Invoke all the {@link #registerEnhancer(ConfigurationEnhancer) registered}
     * {@link ConfigurationEnhancer enhancers} on this {@code Configurer} implementation in their
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
    private void buildModules(LifecycleRegistry lifecycleRegistry) {
        for (Module module : modules.values()) {
            moduleConfigurations.put(module.name(), module.build(config, lifecycleRegistry));
        }
    }

    @Override
    public DefaultComponentRegistry setOverrideBehavior(OverrideBehavior overrideBehavior) {
        this.overrideBehavior = overrideBehavior;
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialized", initialized.get());
        descriptor.describeProperty("components", components);
        descriptor.describeProperty("decorators", decoratorRegistrations);
        descriptor.describeProperty("configurerEnhancers", enhancers);
        descriptor.describeProperty("modules", modules.values());
    }

    /**
     * Initialize the components defined in this registry, allowing them to register their lifecycle actions with given
     * {@code lifecycleRegistry}
     *
     * @param lifecycleRegistry The registry where components may register their lifecycle actions
     */
    private void initializeComponents(LifecycleRegistry lifecycleRegistry) {
        NewConfiguration cfg = config;
        components.postProcessComponents(c -> c.initLifecycle(cfg, lifecycleRegistry));
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

        @Override
        public NewConfiguration getParent() {
            return parent;
        }

        @Nonnull
        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nonnull String name) {
            return components.get(new Identifier<>(type, name))
                             .map(c -> c.resolve(config))
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
                    id -> new Component<>(identifier, c -> fromParent(type, name, defaultImpl))
            ).resolve(this);
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
        public Optional<NewConfiguration> getModuleConfiguration(String name) {
            return Optional.ofNullable(moduleConfigurations.get(name));
        }
    }

    /**
     * Private record representing a {@code decorator} registration. All {@code DecoratorRegistrations} are gathered and
     * invoked during {@link ApplicationConfigurer#build()} of this configurer.
     *
     * @param idMatcher The {@code Predicate} used against a {@link Identifier} to validate if the {@code decorator}
     *                  should be invoked.
     * @param order     The order of the given {@code decorator} among other decorators.
     * @param decorator The decoration function for a component of type {@code C}.
     * @param <C>       The type of component the {@code decorator} decorates.
     */
    private record DecoratorRegistration<C, D extends C>(Predicate<Identifier<C>> idMatcher,
                                            int order,
                                            ComponentDecorator<C, D> decorator) implements DescribableComponent, Predicate<Identifier<C>> {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("order", order);
            descriptor.describeProperty("decorator", decorator);
        }

        @Override
        public boolean test(Identifier<C> identifier) {
            return idMatcher.test(identifier);
        }
    }
}
