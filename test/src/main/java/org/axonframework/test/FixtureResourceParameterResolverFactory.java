/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Priority;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static org.axonframework.common.Priority.LAST;

/**
 * ParameterResolverFactory implementation that is aware of test-specific use cases. It uses a ThreadLocal to keep
 * track of injectable resources.
 * <p/>
 * Although all access to this fixture should be done using the static accessor methods, creation of an instance is
 * still possible to comply with the ServiceLoader specification.
 * <p/>
 * Furthermore, this factory creates lazy parameter resolver, which mean that unsupported parameters are only detected
 * when a specific handler is invoked. This ensures that only the resources that are actually used inside a test need
 * to be configured.
 *
 * @author Allard Buijze
 * @since 2.1
 */
@Priority(LAST)
public final class FixtureResourceParameterResolverFactory implements ParameterResolverFactory {

    private static final ThreadLocal<List<Object>> RESOURCES = new ThreadLocal<>();

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        return new LazyParameterResolver(parameterType);
    }

    /**
     * Registers an additional resource for injection
     *
     * @param injectableResource the resource to inject into handler methods
     */
    public static void registerResource(Object injectableResource) {
        if (RESOURCES.get() == null) {
            RESOURCES.set(new ArrayList<>());
        }
        if (!RESOURCES.get().contains(injectableResource)) {
            RESOURCES.get().add(injectableResource);
        }
    }

    /**
     * Clears the injectable resources registered to the current thread.
     */
    public static void clear() {
        RESOURCES.remove();
    }

    private static class LazyParameterResolver implements ParameterResolver {

        private final Class<?> parameterType;

        public LazyParameterResolver(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            final List<Object> objects = RESOURCES.get();
            if (objects != null) {
                for (Object resource : objects) {
                    if (parameterType.isInstance(resource)) {
                        return resource;
                    }
                }
            }
            throw new FixtureExecutionException("No resource of type [" + parameterType.getName()
                                                        + "] has been registered. It is required for one of the handlers being executed.");
        }

        @Override
        public boolean matches(Message message) {
            return RESOURCES.get() != null;
        }
    }
}
