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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link Component} and {@link ComponentDefinition} that instantiates a component using a configured
 * factory method.
 * <p>
 * For internal use only. Instead, use static methods on {@link ComponentDefinition} to instantiate definitions.
 *
 * @param <C> The declared type of the component.
 * @param <A> The actual (runtime) type of the component.
 * @author Allard Buijze
 * @since 5.0.0
 */
class LazyInitializedComponentDefinition<C, A extends C> extends AbstractComponent<C, A> {

    private final ComponentFactory<A> factory;
    private final AtomicReference<A> instanceReference = new AtomicReference<>();

    /**
     * Create the definition for a component with given {@code identifier} and given {@code instance}.
     *
     * @param identifier The identifier of the component.
     * @param factory    The function used to create an instance of this component.
     */
    LazyInitializedComponentDefinition(@Nonnull Component.Identifier<C> identifier,
                                       @Nonnull ComponentFactory<A> factory) {
        super(identifier);
        this.factory = Objects.requireNonNull(factory, "The factory must not be null.");
    }

    @Override
    public A doResolve(@Nonnull Configuration configuration) {
        A resolvedInstance = instanceReference.get();
        if (resolvedInstance != null) {
            return resolvedInstance;
        }

        synchronized (this) {
            if (instanceReference.get() == null) {
                instanceReference.set(factory.build(configuration));
            }
        }

        return instanceReference.get();
    }

    @Override
    public boolean isInstantiated() {
        return instanceReference.get() != null;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        C instance = instanceReference.get();
        if (instance != null) {
            descriptor.describeProperty("instance", instance);
        } else {
            descriptor.describeProperty("factory", factory);
        }
    }
}
