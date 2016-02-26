/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.annotation;

import org.axonframework.common.Priority;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * ParameterResolverFactory implementation that resolves parameters for a specific given Resource.
 *
 * @author Allard Buijze
 * @since 2.4.2
 */
@Priority(Priority.LOW)
public class SimpleResourceParameterResolverFactory implements ParameterResolverFactory {

    private final Object resource;

    /**
     * Initialize the ParameterResolverFactory to inject the given <code>resource</code> in applicable parameters.
     *
     * @param resource The resource to inject
     */
    public SimpleResourceParameterResolverFactory(Object resource) {
        this.resource = resource;
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (parameters[parameterIndex].getType().isInstance(resource)) {
            return new FixedValueParameterResolver<>(resource);
        }
        return null;
    }
}
