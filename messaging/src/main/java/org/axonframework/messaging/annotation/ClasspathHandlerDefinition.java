/*
 * Copyright (c) 2010-2018. Axon Framework
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
 * HandlerDefinition instance that locates other HandlerDefinition instances on the class path. It uses the {@link
 * ServiceLoader} mechanism to locate and initialize them.
 * <p/>
 * This means for this class to find implementations, their fully qualified class name has to be put into a file called
 * {@code META-INF/services/org.axonframework.messaging.annotation.HandlerDefinition}. For more details, see
 * {@link ServiceLoader}.
 *
 * @author Tyler Thrailkill
 * @author Milan Savic
 * @see ServiceLoader
 * @since 3.3
 */
public final class ClasspathHandlerDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathHandlerDefinition.class);
    private static final Object MONITOR = new Object();
    private static final Map<ClassLoader, WeakReference<MultiHandlerDefinition>> FACTORIES = new WeakHashMap<>();

    private ClasspathHandlerDefinition() {
        // not meant to be publicly instantiated
    }

    /**
     * Creates an instance for the given {@code clazz}. Effectively, the class loader of the given class is used to
     * locate implementations.
     *
     * @param clazz The class for which the handler definition must be returned
     * @return a MultiHandlerDefinition that can create handlers for the given class
     */
    public static MultiHandlerDefinition forClass(Class<?> clazz) {
        return forClassLoader(clazz == null ? null : clazz.getClassLoader());
    }

    /**
     * Creates an instance using the given {@code classLoader}. Implementations are located using this class loader.
     *
     * @param classLoader The class loader to locate the implementations with
     * @return a MultiHandlerDefinition instance using the given classLoader
     */
    public static MultiHandlerDefinition forClassLoader(ClassLoader classLoader) {
        synchronized (MONITOR) {
            MultiHandlerDefinition handlerDefinition;
            if (!FACTORIES.containsKey(classLoader)) {
                handlerDefinition = MultiHandlerDefinition.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(handlerDefinition));
                return handlerDefinition;
            }
            handlerDefinition = FACTORIES.get(classLoader).get();
            if (handlerDefinition == null) {
                handlerDefinition = MultiHandlerDefinition.ordered(findDelegates(classLoader));
                FACTORIES.put(classLoader, new WeakReference<>(handlerDefinition));
            }
            return handlerDefinition;
        }
    }

    private static MultiHandlerDefinition findDelegates(ClassLoader classLoader) {
        Iterator<HandlerDefinition> iterator = load(HandlerDefinition.class, classLoader == null ?
                Thread.currentThread().getContextClassLoader() : classLoader).iterator();
        //noinspection WhileLoopReplaceableByForEach
        final List<HandlerDefinition> definitions = new ArrayList<>();
        while (iterator.hasNext()) {
            try {
                HandlerDefinition factory = iterator.next();
                definitions.add(factory);
            } catch (ServiceConfigurationError e) {
                LOGGER.info("HandlerDefinition instance ignored, as one of the required classes is not availableon the classpath: {}", e.getMessage());
            } catch (NoClassDefFoundError e) {
                LOGGER.info("HandlerDefinition instance ignored. It relies on a class that cannot be found: {}",
                            e.getMessage());
            }
        }
        return new MultiHandlerDefinition(definitions);
    }
}
