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

package org.axonframework.messaging.annotation;

import static java.util.ServiceLoader.load;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

import org.slf4j.LoggerFactory;

/**
 * HandlerEnhancerDefinition instance that locates other HandlerEnhancerDefinition instances on the class path. It uses
 * the {@link ServiceLoader} mechanism to locate and initialize them.
 * <p/>
 * This means for this class to find implementations, their fully qualified class name has to be put into a file called
 * {@code META-INF/services/org.axonframework.messaging.annotation.HandlerEnhancerDefinition}. For more details, see
 * {@link ServiceLoader}.
 *
 * @author Allard Buijze
 * @see ServiceLoader
 * @since 2.1
 */
public final class ClasspathHandlerEnhancerDefinition {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ClasspathHandlerEnhancerDefinition.class);
    private static final Object monitor = new Object();
    private static final Map<ClassLoader, WeakReference<HandlerEnhancerDefinition>> FACTORIES = new WeakHashMap<>();

    /**
     * Private default constructor
     */
    private ClasspathHandlerEnhancerDefinition() {
    }

    /**
     * Creates an instance for the given {@code clazz}. Effectively, the class loader of the given class is used
     * to locate implementations.
     *
     * @param clazz The class for which the parameter resolver must be returned
     * @return a ClasspathParameterResolverFactory that can resolve parameters for the given class
     */
    public static HandlerEnhancerDefinition forClass(Class<?> clazz) {
        return forClassLoader(clazz == null ? null : clazz.getClassLoader());
    }

    /**
     * Creates an instance using the given {@code classLoader}. Implementations are located using this class
     * loader.
     *
     * @param classLoader The class loader to locate the implementations with
     * @return a HandlerEnhancerDefinition instance using the given classLoader
     */
    public static HandlerEnhancerDefinition forClassLoader(ClassLoader classLoader) {
        synchronized (monitor) {
            HandlerEnhancerDefinition factory;
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

    private static List<HandlerEnhancerDefinition> findDelegates(ClassLoader classLoader) {
        Iterator<HandlerEnhancerDefinition> iterator = load(HandlerEnhancerDefinition.class, classLoader == null ?
                Thread.currentThread().getContextClassLoader() : classLoader).iterator();
        //noinspection WhileLoopReplaceableByForEach
        final List<HandlerEnhancerDefinition> factories = new ArrayList<>();
        while (iterator.hasNext()) {
            try {
                HandlerEnhancerDefinition factory = iterator.next();
                factories.add(factory);
            } catch (ServiceConfigurationError e) {
                logger.info(
                        "HandlerEnhancerDefinition instance ignored, as one of the required classes is not available" +
                                "on the classpath: {}", e.getMessage());
            } catch (NoClassDefFoundError e) {
                logger.info("HandlerEnhancerDefinition instance ignored. It relies on a class that cannot be found: {}",
                            e.getMessage());
            }
        }
        return factories;
    }
}
