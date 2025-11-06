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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of a {@link Component} that decorates another component's instance.
 * <p>
 * It is not recommended to use this class directly. Instead, use
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition)} using the {@link DecoratorDefinition}.
 *
 * @param <C> The declared type of the component.
 * @param <D> The implementation type of the decorated component.
 * @author Allard Buijze
 * @see DecoratorDefinition
 * @since 5.0.0
 */
class DecoratedComponent<C, D extends C> extends AbstractComponent<C, D> {

    private final Component<C> delegate;
    private final ComponentDecorator<C, D> decorator;

    private final AtomicReference<D> instanceReference = new AtomicReference<>();

    /**
     * Initializes a component that decorates the given {@code delegate} using given {@code decorator} function.
     * <p>
     * The given {@code startHandlers} and {@code shutdownHandlers} are registered with the lifecycle registry upon
     * initialization of the component.
     *
     * @param delegate         The component to decorate.
     * @param decorator        The decoration function creating an instance of the decorated component.
     * @param startHandlers    A list of handlers to invoke during application startup.
     * @param shutdownHandlers A list of handlers to invoke during application shutdown.
     */
    DecoratedComponent(@Nonnull Component<C> delegate,
                       @Nonnull ComponentDecorator<C, D> decorator,
                       @Nonnull List<AbstractComponent.HandlerRegistration<D>> startHandlers,
                       @Nonnull List<AbstractComponent.HandlerRegistration<D>> shutdownHandlers) {
        super(requireNonNull(delegate, "The delegate cannot be null.").identifier(), startHandlers, shutdownHandlers);
        this.delegate = delegate;
        this.decorator = decorator;
    }

    @Override
    public D doResolve(@Nonnull Configuration configuration) {
        D existingInstance = instanceReference.get();
        if (existingInstance != null) {
            return existingInstance;
        }
        synchronized (this) {
            return instanceReference.updateAndGet(
                    instance -> {
                        if (instance != null) {
                            return instance;
                        }
                        Class<C> originalType = delegate.identifier().typeAsClass();
                        D decorated = decorator.decorate(configuration,
                                                         identifier().name(),
                                                         delegate.resolve(configuration));
                        if (originalType.isAssignableFrom(decorated.getClass())) {
                            return decorated;
                        }
                        throw new ClassCastException(String.format(
                                "Original component type [%s] is not assignable to decorated component type [%s]. "
                                        + "Make sure decorators return matching components, as component retrieval otherwise fails!",
                                originalType,
                                decorated.getClass()
                        ));
                    }
            );
        }
    }

    @Override
    public boolean isInstantiated() {
        return instanceReference.get() != null;
    }

    @Override
    public void initLifecycle(@Nonnull Configuration configuration, @Nonnull LifecycleRegistry lifecycleRegistry) {
        delegate.initLifecycle(configuration, lifecycleRegistry);
        super.initLifecycle(configuration, lifecycleRegistry);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        D instance = instanceReference.get();
        if (instance != null) {
            descriptor.describeProperty("instance", instance);
        } else {
            descriptor.describeProperty("decorator", decorator);
            descriptor.describeWrapperOf(delegate);
        }
    }
}
