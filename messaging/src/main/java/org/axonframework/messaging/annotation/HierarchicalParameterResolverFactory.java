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

package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.axonframework.common.Priority.LOW;

/**
 * {@link ParameterResolverFactory} that first tries to resolve a parameter using the child factory. If that fails, it
 * tries the parent factory. This is useful to encapsulate a set of parameter resolvers that are only relevant in a
 * specific context, such as a specific {@link org.axonframework.configuration.Module}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Priority(LOW)
public class HierarchicalParameterResolverFactory implements ParameterResolverFactory {

    private final ParameterResolverFactory parent;
    private final ParameterResolverFactory child;

    private HierarchicalParameterResolverFactory(ParameterResolverFactory parent, ParameterResolverFactory child) {
        this.parent = parent;
        this.child = child;
    }

    /**
     * Creates a new hierarchical{@link ParameterResolverFactory} that delegates to the given {@code parent} and
     * {@code child} factories. The {@code child} factory is tried first, and if it cannot resolve the parameter, the
     * {@code parent} factory is tried.
     *
     * @param parent The parent {@link ParameterResolverFactory} to delegate to if the child cannot resolve the
     *               parameter.
     * @param child  The child {@link ParameterResolverFactory} to try first.
     * @return A new hierarchical {@link ParameterResolverFactory} that delegates to the given factories.
     */
    public static HierarchicalParameterResolverFactory create(ParameterResolverFactory parent,
                                                              ParameterResolverFactory child) {
        return new HierarchicalParameterResolverFactory(parent, child);
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        ParameterResolver<?> resolver = child.createInstance(executable, parameters, parameterIndex);
        if (resolver != null) {
            return resolver;
        }
        return parent.createInstance(executable, parameters, parameterIndex);
    }
}
