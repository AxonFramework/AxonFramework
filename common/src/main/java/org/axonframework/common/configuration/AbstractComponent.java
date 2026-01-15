/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Base implementation of a {@link Component} and
 * {@link ComponentDefinition.ComponentCreator} to simplify definition and creation of
 * components through a {@link ComponentDefinition}.
 *
 * @param <C> The declared type of the component.
 * @param <A> The actual implementation type of the component.
 * @author Allard Buijze
 * @since 3.0.0
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
     * @param startHandlers    A list of preconfigured startup handlers for this component.
     * @param shutdownHandlers A list of preconfigured shutdown handlers for this component.
     */
    protected AbstractComponent(@Nonnull Identifier<C> identifier,
                                @Nonnull List<HandlerRegistration<A>> startHandlers,
                                @Nonnull List<HandlerRegistration<A>> shutdownHandlers) {
        this(identifier);
        this.startHandlers.addAll(requireNonNull(startHandlers, "The start handlers must not be null."));
        this.shutdownHandlers.addAll(requireNonNull(shutdownHandlers, "The shutdown handlers must not be null."));
    }

    /**
     * Initialize the component with given {@code identifier}.
     *
     * @param identifier The identifier of the component.
     */
    protected AbstractComponent(@Nonnull Identifier<C> identifier) {
        this.identifier = requireNonNull(identifier, "The identifier must not be null.");
    }

    @Override
    public Component<C> createComponent() {
        return this;
    }

    @Override
    public ComponentDefinition<C> onStart(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {
        this.startHandlers.add(new HandlerRegistration<>(
                phase, requireNonNull(handler, "The start handler must not be null.")
        ));
        return this;
    }

    @Override
    public ComponentDefinition<C> onShutdown(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {
        this.shutdownHandlers.add(new HandlerRegistration<>(
                phase, requireNonNull(handler, "The shutdown handler must not be null.")
        ));
        return this;
    }

    @Override
    public Identifier<C> identifier() {
        return this.identifier;
    }

    @Override
    public Class<C> rawType() {
        return this.identifier.typeAsClass();
    }

    @Nullable
    @Override
    public String name() {
        return this.identifier.name();
    }

    @Override
    public C resolve(@Nonnull Configuration configuration) {
        return doResolve(requireNonNull(configuration, "The configuration must not be null."));
    }

    /**
     * Method invoked by {@link #resolve(Configuration)}, allowing for customization per {@code AbstractComponent}
     * implementation.
     *
     * @param configuration The configuration that declared this component.
     * @return The resolved instance defined in this component.
     */
    abstract A doResolve(@Nonnull Configuration configuration);

    @Override
    public void initLifecycle(@Nonnull Configuration configuration,
                              @Nonnull LifecycleRegistry lifecycleRegistry) {
        requireNonNull(configuration, "The configuration must not be null.");
        requireNonNull(lifecycleRegistry, "The lifecycle registry must not be null.");

        if (!initialized.getAndSet(true)) {
            startHandlers.forEach(
                    startHandler -> lifecycleRegistry.onStart(
                            startHandler.phase,
                            () -> startHandler.handler.run(configuration, doResolve(configuration))
                    )
            );
            shutdownHandlers.forEach(
                    shutdownHandler -> lifecycleRegistry.onShutdown(
                            shutdownHandler.phase,
                            () -> shutdownHandler.handler.run(configuration, doResolve(configuration))
                    )
            );
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
     * @param handler The handler to execute.
     * @param <C>     The type of component.
     */
    public record HandlerRegistration<C>(int phase, @Nonnull ComponentLifecycleHandler<C> handler) {

        /**
         * Initializes an entry holding the lifecycle {@code handler} and its corresponding {@code phase}.
         *
         * @param phase   The lifecycle phase to execute the handler in.
         * @param handler The handler to execute.
         */
        public HandlerRegistration {
            requireNonNull(handler, "The lifecycle handler must not be null.");
        }
    }
}
