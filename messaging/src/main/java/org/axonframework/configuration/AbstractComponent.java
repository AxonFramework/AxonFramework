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
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of a {@link Component} and
 * {@link org.axonframework.configuration.ComponentDefinition.ComponentCreator} to simplify definition and creation of
 * components through a {@link ComponentDefinition}.
 *
 * @param <C> The declared type of the Component
 * @param <A> The actual implementation type of the Component
 */
public abstract class AbstractComponent<C, A extends C>
        implements ComponentDefinition.ComponentCreator<C>, Component<C> {

    private final Identifier<C> identifier;
    private final List<HandlerRegistration<? super A>> startHandlers = new CopyOnWriteArrayList<>();
    private final List<HandlerRegistration<? super A>> shutdownHandlers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize the component with given {@code identifier} and given preconfigured {@code startHandlers} and
     * {@code shutdownHandlers} lifecycle handlers.
     *
     * @param identifier       The identifier of the component.
     * @param startHandlers    a list of preconfigured startup handlers for this component.
     * @param shutdownHandlers a list of preconfigured shutdown handlers for this component.
     */
    protected AbstractComponent(@Nonnull Identifier<C> identifier,
                                @Nonnull List<HandlerRegistration<A>> startHandlers,
                                @Nonnull List<HandlerRegistration<A>> shutdownHandlers) {
        this(identifier);
        this.startHandlers.addAll(startHandlers);
        this.shutdownHandlers.addAll(shutdownHandlers);
    }

    /**
     * Initialize the component with given {@code identifier}.
     *
     * @param identifier The identifier of the component.
     */
    protected AbstractComponent(@Nonnull Identifier<C> identifier) {
        this.identifier = Objects.requireNonNull(identifier, "identifier must not be null");
    }

    @Override
    public Component<C> createComponent() {
        return this;
    }

    @Override
    public ComponentDefinition<C> onStart(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        this.startHandlers.add(new HandlerRegistration<>(phase, handler));
        return this;
    }

    @Override
    public ComponentDefinition<C> onShutdown(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        this.shutdownHandlers.add(new HandlerRegistration<>(phase, handler));
        return this;
    }

    @Override
    public C resolve(@Nonnull NewConfiguration configuration) {
        return doResolve(Objects.requireNonNull(configuration, "configuration must not be null"));
    }

    abstract A doResolve(@Nonnull NewConfiguration configuration);

    @Override
    public Identifier<C> identifier() {
        return identifier;
    }

    @Override
    public void initLifecycle(@Nonnull NewConfiguration configuration, @Nonnull LifecycleRegistry lifecycleRegistry) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(lifecycleRegistry, "lifecycleRegistry must not be null");
        if (!initialized.getAndSet(true)) {
            startHandlers.forEach(startHandler ->
                                          lifecycleRegistry.onStart(startHandler.phase,
                                                                    () -> startHandler.handler.run(configuration,
                                                                                                   doResolve(
                                                                                                           configuration))));

            shutdownHandlers.forEach(shutdownHandler ->
                                             lifecycleRegistry.onShutdown(shutdownHandler.phase,
                                                                          () -> shutdownHandler.handler.run(
                                                                                  configuration,
                                                                                  doResolve(configuration))));
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized.get();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("identifier", identifier);
        descriptor.describeProperty("initialized", isInitialized());
        descriptor.describeProperty("instantiated", isInstantiated());
    }

    /**
     * An entry holding the lifecycle {@code handler} and its corresponding {@code phase}.
     *
     * @param phase   The lifecycle phase to execute the handler in.
     * @param handler The handler to execute
     * @param <C>     The type of component
     */
    public record HandlerRegistration<C>(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {

        /**
         * Initializes an entry holding the lifecycle {@code handler} and its corresponding {@code phase}.
         *
         * @param phase   The lifecycle phase to execute the handler in.
         * @param handler The handler to execute
         */
        public HandlerRegistration {
            Objects.requireNonNull(handler, "handler must not be null");
        }
    }
}
