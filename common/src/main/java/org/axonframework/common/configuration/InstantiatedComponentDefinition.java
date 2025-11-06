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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.Collections;
import java.util.Objects;

/**
 * Implementation of {@link Component} and {@link ComponentDefinition} that wraps a pre-instantiated component.
 * <p>
 * For internal use only. Instead, use static methods on {@link ComponentDefinition} to instantiate definitions.
 *
 * @param <C> The declared type of the component.
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public class InstantiatedComponentDefinition<C> extends AbstractComponent<C, C> {

    private final C instance;

    /**
     * Create the definition for a component with given {@code identifier} and given {@code instance}.
     *
     * @param identifier The identifier of the component.
     * @param instance   The instance the components resolves to.
     */
    public InstantiatedComponentDefinition(@Nonnull Component.Identifier<C> identifier,
                                           @Nonnull C instance) {
        super(identifier, Collections.emptyList(), Collections.emptyList());
        this.instance = Objects.requireNonNull(instance, "The instance must not be null.");
    }

    @Override
    public C doResolve(@Nonnull Configuration configuration) {
        return instance;
    }

    @Override
    public boolean isInstantiated() {
        return true;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        descriptor.describeProperty("instance", instance);
    }
}
