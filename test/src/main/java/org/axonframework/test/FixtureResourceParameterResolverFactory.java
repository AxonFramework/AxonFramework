/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.test;

import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ParameterResolverFactory implementation that is aware of test-specific use cases. It can be disabled after the
 * necessary annotated handler have been processed.
 * <p/>
 * Furthermore, this factory creates lazy parameter resolver, which mean that unsupported parameters are only detected
 * when a specific handler is invoked. This ensures that only the resources that are actually used inside a test need
 * to be configured.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public final class FixtureResourceParameterResolverFactory implements ParameterResolverFactory {

    private final List<Object> injectableResources = new ArrayList<Object>();
    private final ParameterResolverFactory delegate;

    /**
     * Initializes the ParameterResolverFactory that allows for the given <code>initialResources</code> to be injected
     * as handler method parameters.
     *
     * @param targetClass      The class for which the injected parameters must be available
     * @param initialResources The resources to make available for injection
     */
    public FixtureResourceParameterResolverFactory(Class<?> targetClass, Object... initialResources) {
        injectableResources.addAll(Arrays.asList(initialResources));
        delegate = ClasspathParameterResolverFactory.forClass(targetClass);
    }

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        ParameterResolver parameterResolver = delegate.createInstance(memberAnnotations, parameterType,
                                                                      parameterAnnotations);
        if (parameterResolver != null) {
            return parameterResolver;
        }
        return new LazyParameterResolver(parameterType, injectableResources);
    }

    /**
     * Registers an additional resource for injection
     *
     * @param injectableResource the resource to inject into handler methods
     */
    public void registerResource(Object injectableResource) {
        if (!injectableResources.contains(injectableResource)) {
            injectableResources.add(injectableResource);
        }
    }

    private static class LazyParameterResolver implements ParameterResolver {

        private final Class<?> parameterType;
        private final List<Object> injectableResources;

        public LazyParameterResolver(Class<?> parameterType, List<Object> injectableResources) {
            this.parameterType = parameterType;
            this.injectableResources = injectableResources;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            for (Object resource : injectableResources) {
                if (parameterType.isInstance(resource)) {
                    return resource;
                }
            }
            throw new FixtureExecutionException("No resource of type [" + parameterType.getName()
                                                        + "] has been registered. It is required for one of the handlers being executed.");
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }
}
