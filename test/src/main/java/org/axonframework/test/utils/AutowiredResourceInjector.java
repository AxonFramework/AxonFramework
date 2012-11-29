/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.test.utils;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.test.FixtureExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.axonframework.common.ReflectionUtils.methodsOf;

/**
 * Resource injector that uses setter methods to inject resources. All methods starting with "set" are evaluated. If
 * that method has a single parameter, a Resource of that type is injected into it, if present.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class AutowiredResourceInjector implements ResourceInjector {

    private Iterable<?> resources;

    /**
     * Initializes the resource injector to inject to given <code>resources</code>.
     *
     * @param resources The resources to inject
     */
    public AutowiredResourceInjector(Iterable<?> resources) {
        this.resources = resources;
    }

    @Override
    public void injectResources(Saga saga) {
        for (Method method : methodsOf(saga.getClass())) {
            if (isSetter(method)) {
                Class<?> requiredType = method.getParameterTypes()[0];
                for (Object resource : resources) {
                    if (requiredType.isInstance(resource)) {
                        injectResource(saga, method, resource);
                    }
                }
            }
        }
    }

    private void injectResource(Saga saga, Method setterMethod, Object resource) {
        try {
            ReflectionUtils.ensureAccessible(setterMethod);
            setterMethod.invoke(saga, resource);
        } catch (IllegalAccessException e) {
            throw new FixtureExecutionException("An exception occurred while trying to inject a resource", e);
        } catch (InvocationTargetException e) {
            throw new FixtureExecutionException("An exception occurred while trying to inject a resource", e);
        }
    }

    private boolean isSetter(Method method) {
        return method.getParameterTypes().length == 1 && method.getName().startsWith("set");
    }
}
