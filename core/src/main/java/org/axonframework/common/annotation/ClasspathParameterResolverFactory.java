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

package org.axonframework.common.annotation;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ParameterResolverFactory instance that locates other ParameterResolverFactory instances on the class path. It uses
 * the {@link ServiceLoader} mechanism to locate and initialize them.
 * <p/>
 * This means for this class to find implementations, their fully qualified class name has to be put into a file called
 * <code>META-INF/services/org.axonframework.common.annotation.ParameterResolverFactory</code>. For more details, see
 * {@link ServiceLoader}.
 *
 * @author Allard Buijze
 * @see ServiceLoader
 * @since 2.1
 */
public class ClasspathParameterResolverFactory implements ParameterResolverFactory {

    private final List<ParameterResolverFactory> RESOLVERS = new CopyOnWriteArrayList<ParameterResolverFactory>();

    private static final Object monitor = new Object();
    private static final Map<ClassLoader, WeakReference<ClasspathParameterResolverFactory>> factories =
            new WeakHashMap<ClassLoader, WeakReference<ClasspathParameterResolverFactory>>();

    /**
     * Creates an instance for the given <code>clazz</code>. Effectively, the class loader of the given class is used
     * to locate implementations.
     *
     * @param clazz The class for which the parameter resolver must be returned
     * @return a ClasspathParameterResolverFactory that can resolve parameters for the given class
     */
    public static ClasspathParameterResolverFactory forClass(Class<?> clazz) {
        return forClassLoader(clazz == null ? null : clazz.getClassLoader());
    }

    /**
     * Creates an instance using the given <code>classLoader</code>. Implementations are located using this class
     * loader.
     *
     * @param classLoader The class loader to locate the implementations with
     * @return a ClasspathParameterResolverFactory instance using the given classLoader
     */
    public static ClasspathParameterResolverFactory forClassLoader(ClassLoader classLoader) {
        synchronized (monitor) {
            ClasspathParameterResolverFactory factory;
            if (!factories.containsKey(classLoader)) {
                factory = new ClasspathParameterResolverFactory(classLoader);
                factories.put(classLoader, new WeakReference<ClasspathParameterResolverFactory>(factory));
                return factory;
            }
            factory = factories.get(classLoader).get();
            if (factory == null) {
                factory = new ClasspathParameterResolverFactory(classLoader);
                factories.put(classLoader, new WeakReference<ClasspathParameterResolverFactory>(factory));
            }
            return factory;
        }
    }

    private ClasspathParameterResolverFactory(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        for (ParameterResolverFactory factory : ServiceLoader.load(ParameterResolverFactory.class, classLoader)) {
            RESOLVERS.add(factory);
        }
    }

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        for (ParameterResolverFactory factory : RESOLVERS) {
            ParameterResolver resolver = factory.createInstance(memberAnnotations, parameterType, parameterAnnotations);
            if (resolver != null) {
                return resolver;
            }
        }
        return null;
    }
}
