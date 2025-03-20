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
import org.axonframework.configuration.Component.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Abstract implementation of the {@link NewConfigurer} allowing for reuse of {@link Component},
 * {@link ComponentDecorator}, {@link ConfigurationEnhancer}, and {@link Module} registration for the
 * {@code NewConfigurer} and {@link Module} implementations alike.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class AbstractConfigurer<S extends NewConfigurer<S>> implements NewConfigurer<S> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Components components = new Components();
    private final List<ConfigurationEnhancer> enhancers = new ArrayList<>();
    private final List<Module<?>> modules = new ArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<RegisteredComponentDecorator<?>> componentDecorators = new ArrayList<>();
    private LifecycleSupportingConfiguration parent;
    private LifecycleSupportingConfiguration config;
    private final List<NewConfiguration> moduleConfigurations = new ArrayList<>();

    protected final NewConfiguration config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private OverrideBehavior overrideBehavior = OverrideBehavior.WARN;

    /**
     * Initialize the {@code AbstractConfigurer} based on the given {@code parent}.
     *
     * @param parent The life cycle supporting configuration used as the <b>parent</b> configuration of the
     *               {@link LocalConfiguration}.
     */
    protected AbstractConfigurer(@Nullable NewConfiguration parent) {
        this.config = new LocalConfiguration(parent);
    }

    @Override
    public <C> S registerComponent(@Nonnull Class<C> type,
                                   @Nonnull String name,
                                   @Nonnull ComponentFactory<C> factory) {
        logger.debug("Registering component [{}] of type [{}].", name, type);
        Identifier<C> identifier = new Identifier<>(type, name);
        if (overrideBehavior == OverrideBehavior.THROW && components.contains(identifier)) {
            throw new ComponentOverrideException(type, name);
        }

        Component<C> previous = components.put(identifier, new Component<>(identifier, this::config, factory));
        if (previous != null && overrideBehavior == OverrideBehavior.WARN) {
            logger.warn("Replaced a previous Component registered for type [{}] and name [{}].", name, type);
        }
        return self();
    }

    @Override
    public <C> S registerDecorator(@Nonnull Class<C> type,
                                   int order,
                                   @Nonnull ComponentDecorator<C> decorator) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(decorator, "decorator must not be null");
        logger.debug("Registering decorator for type [{}] at order #{}.", type, order);
        componentDecorators.add(new RegisteredComponentDecorator<>(i -> i.type().equals(type), order, decorator));
        return self();
    }

    @Override
    public <C> S registerDecorator(@Nonnull Class<C> type, @Nonnull String name, int order,
                                   @Nonnull ComponentDecorator<C> decorator) {
        Objects.requireNonNull(name, "name must not be null");
        logger.debug("Registering decorator for name [{}] and type [{}] at order #{}.", name, type, order);
        componentDecorators.add(new RegisteredComponentDecorator<>(
                i -> Objects.equals(i.name(), name) && Objects.equals(
                        i.type(),
                        type), order, decorator));
        return self();
    }

    private S self() {
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nonnull String name) {
        return components.contains(new Component.Identifier<>(type, name));
    }

    @Override
    public S registerEnhancer(@Nonnull ConfigurationEnhancer enhancer) {
        logger.debug("Registering enhancer [{}].", enhancer.getClass().getSimpleName());
        this.enhancers.add(enhancer);
        return self();
    }

    @Override
    public S registerModule(@Nonnull Module<?> module) {
        logger.debug("Registering module [{}].", module.name());
        this.modules.add(module);
        return self();
    }

    @Override
    public <C extends NewConfigurer<C>> S delegate(@Nonnull Class<C> type,
                                                   @Nonnull Consumer<C> configureTask) {
        logger.warn("Ignoring configure task on configurer [{}] because there is no delegate configurer of type [{}].",
                    this.getClass(), type);
        return self();
    }

    @Override
    public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        throw new UnsupportedOperationException("Registering start handlers is not supported on this configurer.");
    }

    @Override
    public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        throw new UnsupportedOperationException("Registering shutdown handlers is not supported on this configurer.");
    }

    /**
     * Sets the given {@code parent} as the parent configuration of the {@link LocalConfiguration} of this configurer.
     *
     * @param parent The parent configuration of the {@link LocalConfiguration} of this configurer.
     */
    protected void setParent(@Nullable LifecycleSupportingConfiguration parent) {
        this.parent = parent;
    }

    /**
     * Common builder activity for any {@code AbstractConfigurer} implementation.
     * <p>
     * Will enhance this configurer will all registered {@link ConfigurationEnhancer ConfigurationEnhancers} and
     * {@link Module#build(LifecycleSupportingConfiguration) builds} all the {@link Module Modules}.
     */
    protected void enhanceInvocationAndModuleConstruction() {
        if (!initialized.getAndSet(true)) {
            invokeEnhancers();
            decorateComponents();
            buildModules();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void decorateComponents() {
        componentDecorators.sort(Comparator.comparingInt(RegisteredComponentDecorator::order));
        for (RegisteredComponentDecorator componentDecorator : componentDecorators) {
            for (Identifier id : components.listComponents()) {
                if (componentDecorator.componentMatcher.test(id)) {
                    components.replace(id, previous -> previous.decorate(componentDecorator.decorator));
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
    private void buildModules() {
        for (Module<?> module : modules) {
            moduleConfigurations.add(module.build(config()));
        }
    }

    /**
     * Returns the {@link NewConfiguration} of this {@link NewConfigurer} implementation.
     * <p>
     * Will construct it if it has not been initialized yet.
     *
     * @return The {@link NewConfiguration} of this {@link NewConfigurer} implementation.
     */
    protected NewConfiguration config() {
        if (this.config == null) {
            this.config = new LocalConfiguration(parent);
        }
        return this.config;
    }

    /**
     * Sets the {@link OverrideBehavior} for this configurer.
     * <p>
     * Intended for the {@link DefaultAxonApplication} to invoke on
     * {@link AxonApplication#registerOverrideBehavior(OverrideBehavior)}.
     *
     * @param overrideBehavior The override behavior for this {@code AbstractConfigurer}, intended for the
     *                         {@link DefaultAxonApplication} to use on
     *                         {@link AxonApplication#registerOverrideBehavior(OverrideBehavior)} invocations.
     */
    protected void setOverrideBehavior(OverrideBehavior overrideBehavior) {
        this.overrideBehavior = overrideBehavior;
    }

    /**
     * A {@link NewConfiguration} implementation acting as the local configuration of this configurer. Can be
     * implemented by {@link AbstractConfigurer} implementation that need to reuse the access logic for
     * {@link Component Components} and {@link Module Modules} as provided by this implementation.
     */
    public class LocalConfiguration implements NewConfiguration {

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
                             .map(component -> component.init(config(), AbstractConfigurer.this))
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
                    id -> new Component<>(
                            identifier,
                            c -> optionalFromParent(type, name, defaultImpl).orElseGet(defaultImpl)
                    )
            ).init(config(), AbstractConfigurer.this);
            return identifier.type().cast(component);
        }

        private <C> C fromParent(Class<C> type, String name, Supplier<C> defaultSupplier) {
            return parent != null
                    ? parent.getOptionalComponent(type, name).orElseGet(defaultSupplier)
                    : defaultSupplier.get();
        }

        @Override
        public List<NewConfiguration> getModuleConfigurations() {
            return List.copyOf(moduleConfigurations);
        }
    }

    private record RegisteredComponentDecorator<C>(Predicate<Identifier<C>> componentMatcher,
                                                   int order,
                                                   ComponentDecorator<C> decorator) {

    }
}
