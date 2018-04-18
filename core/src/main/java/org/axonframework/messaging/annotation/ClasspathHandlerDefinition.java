/*
 * Copyright (c) 2010-2018. Axon Framework
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

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HandlerDefinition instance that locates other HandlerDefinition instances on the class path. It uses
 * the {@link ServiceLoader} mechanism to locate and initialize them.
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
public final class ClasspathHandlerDefinition implements HandlerDefinition {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathHandlerDefinition.class);
    private MultiHandlerDefinition multiHandlerDefinition;

    /**
     * Initializes classpath handler definition with given class loader. Using this class loader, handler definitions
     * are found on classpath.
     *
     * @param classLoader used to load handler definitions
     */
    public ClasspathHandlerDefinition(ClassLoader classLoader) {
        Iterator<HandlerDefinition> iterator = load(HandlerDefinition.class, classLoader == null ?
                Thread.currentThread().getContextClassLoader() : classLoader).iterator();
        //noinspection WhileLoopReplaceableByForEach
        final List<HandlerDefinition> definitions = new ArrayList<>();
        while (iterator.hasNext()) {
            try {
                HandlerDefinition factory = iterator.next();
                definitions.add(factory);
            } catch (ServiceConfigurationError e) {
                logger.info(
                        "HandlerDefinition instance ignored, as one of the required classes is not available" +
                                "on the classpath: {}", e.getMessage());
            } catch (NoClassDefFoundError e) {
                logger.info("HandlerDefinition instance ignored. It relies on a class that cannot be found: {}",
                            e.getMessage());
            }
        }
        multiHandlerDefinition = new MultiHandlerDefinition(definitions);
    }

    /**
     * Gets the handler definitions found on classpath.
     *
     * @return handler definitions found on classpath
     */
    public List<HandlerDefinition> handlerDefinitions() {
        return multiHandlerDefinition.getDelegates();
    }

    @Override
    public <T> Optional<MessageHandlingMember<T>> createHandler(Class<T> declaringType,
                                                                Executable executable,
                                                                ParameterResolverFactory parameterResolverFactory) {
        return multiHandlerDefinition.createHandler(declaringType, executable, parameterResolverFactory);
    }
}
