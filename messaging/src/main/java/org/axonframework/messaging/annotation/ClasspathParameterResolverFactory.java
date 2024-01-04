/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;

import static java.util.ServiceLoader.load;

/**
 * ParameterResolverFactory instance that locates other ParameterResolverFactory instances on the class path. It uses
 * the {@link ServiceLoader} mechanism to locate and initialize them.
 * <p/>
 * This means for this class to find implementations, their fully qualified class name has to be put into a file called
 * {@code META-INF/services/org.axonframework.messaging.annotation.ParameterResolverFactory}. For more details, see
 * {@link ServiceLoader}.
 *
 * @author Allard Buijze
 * @see ServiceLoader
 * @since 2.1
 */
public final class ClasspathParameterResolverFactory {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ClasspathParameterResolverFactory.class);
    private static final Object monitor = new Object();
    private static final Map<ClassLoader, WeakReference<ParameterResolverFactory>> FACTORIES = new WeakHashMap<>();

    /**
     * Private default constructor
     */
    private ClasspathParameterResolverFactory() {
    }

    /**
     * Creates an instance for the given {@code clazz}. Effectively, the class loader of the given class is used
     * to locate implementations.
     *
     * @param clazz The class for which the parameter resolver must be returned
     * @return a ClasspathParameterResolverFactory that can resolve parameters for the given class
     */
    public static ParameterResolverFactory forClass(Class<?> clazz) {
        return forClassLoader(clazz == null ? null : clazz.getClassLoader());
    }

    /**
     * Creates an instance using the given {@code classLoader}. Implementations are located using this class
     * loader.
     *
     * @param classLoader The class loader to locate the implementations with
     * @return a ParameterResolverFactory instance using the given classLoader
     */
    public static ParameterResolverFactory forClassLoader(ClassLoader classLoader) {
        synchronized (monitor) {
            ParameterResolverFactory factory;
            if (!FACTORIES.containsKey(classLoader)) {
                factory = MultiParameterResolverFactory.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(factory));
                return factory;
            }
            factory = FACTORIES.get(classLoader).get();
            if (factory == null) {
                factory = MultiParameterResolverFactory.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(factory));
            }
            return factory;
        }
    }

    private static List<ParameterResolverFactory> findDelegates(ClassLoader classLoader) {
        Iterator<ParameterResolverFactory> iterator = load(ParameterResolverFactory.class, classLoader == null ?
                Thread.currentThread().getContextClassLoader() : classLoader).iterator();
        //noinspection WhileLoopReplaceableByForEach
        final List<ParameterResolverFactory> factories = new ArrayList<>();
        while (iterator.hasNext()) {
            try {
                ParameterResolverFactory factory = iterator.next();
                factories.add(factory);
            } catch (ServiceConfigurationError e) {
                logger.info("ParameterResolverFactory instance ignored, as one of the required classes is not availableon the classpath: {}", e.getMessage());
            } catch (NoClassDefFoundError e) {
                logger.info("ParameterResolverFactory instance ignored. It relies on a class that cannot be found: {}",
                            e.getMessage());
            }
        }
        return factories;
    }
}
