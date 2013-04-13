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

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
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
 * @since 2.0.1, 2.1
 */
public class FixtureResourceParameterResolverFactory extends ParameterResolverFactory {

    private final List<Object> injectableResources;

    private FixtureResourceParameterResolverFactory(List<Object> injectableResources) {
        this.injectableResources = new ArrayList<Object>(injectableResources);
    }

    /**
     * Registers a ParameterResolverFactory capable of injecting the given <code>injectableResources</code> in
     * annotated
     * handlers.
     *
     * @param injectableResources The resources eligible for injection
     * @return a ParameterResolverFactory capable of injecting the given resources
     */
    public static FixtureResourceParameterResolverFactory register(List<Object> injectableResources) {
        FixtureResourceParameterResolverFactory factory = new FixtureResourceParameterResolverFactory(
                injectableResources);
        ParameterResolverFactory.registerFactory(factory);
        return factory;
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }

    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                               Annotation[] parameterAnnotations) {
        return new LazyParameterResolver(parameterType, injectableResources);
    }

    /**
     * Disables this factory. When a factory is disabled, it doesn't return any ParameterResolvers.
     * <p/>
     * As ParameterResolvers aren't designed to come and go at runtime, this allows factories created during tests to
     * be ignored for other tests that run in the same JVM.
     */
    public void disable() {
        ParameterResolverFactory.unregisterFactory(this);
    }

    private class LazyParameterResolver implements ParameterResolver {

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
