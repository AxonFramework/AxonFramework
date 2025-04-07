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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of a {@link Component} that decorates another component's instance.
 * <p>
 * It is not recommended to use this class directly. Instead, use
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition)} using the {@link DecoratorDefinition}.
 *
 * @param <C> The declared type of the component
 * @param <D> The implementation type of the decorated component
 * @see DecoratorDefinition
 */
public class DecoratedComponent<C, D extends C> extends AbstractComponent<C, D> {

    private final Component<C> delegate;
    private final ComponentDecorator<C, D> decorator;

    private final AtomicReference<D> instance = new AtomicReference<>();

    /**
     * Initializes a Component that decorates the given {@code delegate} using given {@code decorator} function. The
     * given {@code startHandlers} and {@code shutdownHandlers} are registered with the lifecycle registry upon
     * initialization of the component
     *
     * @param delegate         The component to decorate
     * @param decorator        The decoration function creating an instance of the decorated component
     * @param startHandlers    A list of handlers to invoke during application startup
     * @param shutdownHandlers A list of handlers to invoke during application shutdown
     */
    public DecoratedComponent(@Nonnull Component<C> delegate,
                              @Nonnull ComponentDecorator<C, D> decorator,
                              @Nonnull List<AbstractComponent.HandlerRegistration<D>> startHandlers,
                              @Nonnull List<AbstractComponent.HandlerRegistration<D>> shutdownHandlers) {
        super(delegate.identifier(), startHandlers, shutdownHandlers);
        this.delegate = delegate;
        this.decorator = decorator;
    }

    @Override
    public D doResolve(@Nonnull NewConfiguration configuration) {
        D existingInstance = instance.get();
        if (existingInstance != null) {
            return existingInstance;
        }
        synchronized (this) {
            return instance.updateAndGet(i -> i != null ? i :
                    decorator.decorate(configuration,
                                       identifier().name(),
                                       delegate.resolve(configuration))

            );
        }
    }

    @Override
    public void initLifecycle(@Nonnull NewConfiguration configuration, @Nonnull LifecycleRegistry lifecycleRegistry) {
        delegate.initLifecycle(configuration, lifecycleRegistry);
        super.initLifecycle(configuration, lifecycleRegistry);
    }

    @Override
    public boolean isInstantiated() {
        return instance.get() != null;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        D preCreated = instance.get();
        if (preCreated != null) {
            descriptor.describeProperty("instance", preCreated);
        } else {
            descriptor.describeProperty("decorator", decorator);
            descriptor.describeWrapperOf(delegate);
        }
    }
}
