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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

import static java.util.ServiceLoader.load;

/**
 * HandlerEnhancerDefinition instance that locates other HandlerEnhancerDefinition instances on the class path. It uses
 * the {@link ServiceLoader} mechanism to locate and initialize them.
 * <p/>
 * This means for this class to find implementations, their fully qualified class name has to be put into a file called
 * {@code META-INF/services/org.axonframework.messaging.annotation.HandlerEnhancerDefinition}. For more details, see
 * {@link ServiceLoader}.
 *
 * @author Tyler Thrailkill
 * @author Milan Savic
 * @see ServiceLoader
 * @since 3.3
 */
public final class ClasspathHandlerEnhancerDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathHandlerEnhancerDefinition.class);
    private static final Object MONITOR = new Object();
    private static final Map<ClassLoader, WeakReference<MultiHandlerEnhancerDefinition>> FACTORIES = new WeakHashMap<>();

    private ClasspathHandlerEnhancerDefinition() {
        // not meant to be publicly instantiated
    }

    /**
     * Creates an instance for the given {@code clazz}. Effectively, the class loader of the given class is used to
     * locate implementations.
     *
     * @param clazz The class for which the handler definition must be returned
     * @return a MultiHandlerEnhancerDefinition that can create handlers for the given class
     */
    public static MultiHandlerEnhancerDefinition forClass(Class<?> clazz) {
        return forClassLoader(clazz == null ? null : clazz.getClassLoader());
    }

    /**
     * Creates an instance using the given {@code classLoader}. Implementations are located using this class loader.
     *
     * @param classLoader The class loader to locate the implementations with
     * @return a MultiHandlerEnhancerDefinition instance using the given classLoader
     */
    public static MultiHandlerEnhancerDefinition forClassLoader(ClassLoader classLoader) {
        synchronized (MONITOR) {
            MultiHandlerEnhancerDefinition enhancerDefinition;
            if (!FACTORIES.containsKey(classLoader)) {
                enhancerDefinition = MultiHandlerEnhancerDefinition.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(enhancerDefinition));
                return enhancerDefinition;
            }
            enhancerDefinition = FACTORIES.get(classLoader).get();
            if (enhancerDefinition == null) {
                enhancerDefinition = MultiHandlerEnhancerDefinition.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(enhancerDefinition));
            }
            return enhancerDefinition;
        }
    }

    private static MultiHandlerEnhancerDefinition findDelegates(ClassLoader classLoader) {
        Iterator<HandlerEnhancerDefinition> iterator = load(HandlerEnhancerDefinition.class, classLoader == null ?
                Thread.currentThread().getContextClassLoader() : classLoader).iterator();
        //noinspection WhileLoopReplaceableByForEach
        final List<HandlerEnhancerDefinition> enhancers = new ArrayList<>();
        while (iterator.hasNext()) {
            try {
                HandlerEnhancerDefinition factory = iterator.next();
                enhancers.add(factory);
            } catch (ServiceConfigurationError e) {
                LOGGER.info("HandlerEnhancerDefinition instance ignored, as one of the required classes is not availableon the classpath: {}", e.getMessage());
            } catch (NoClassDefFoundError e) {
                LOGGER.info("HandlerEnhancerDefinition instance ignored. It relies on a class that cannot be found: {}",
                            e.getMessage());
            }
        }
        return new MultiHandlerEnhancerDefinition(enhancers);
    }

}
