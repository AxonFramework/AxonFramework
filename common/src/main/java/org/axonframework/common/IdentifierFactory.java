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

package org.axonframework.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Abstract Factory class that provides access to an IdentifierFactory implementation. The IdentifierFactory
 * is responsible for generated unique identifiers for domain objects, such as Aggregates (AggregateRoot) and Events.
 * <p/>
 * This class uses the {@link ServiceLoader} mechanism to find implementations. If none are found, it defaults to an
 * implementation that provides randomly chosen {@code java.util.UUID}s.
 * <p/>
 * To provide your own implementation, create a file called {@code org.axonframework.common.IdentifierFactory} in
 * the {@code META-INF/services} package. The file must contain the fully qualified class name of the
 * implementation to use. This implementation must have a public no-arg constructor and extend IdentifierFactory.
 * <p/>
 * This class is thread safe to use.
 *
 * @author Allard Buijze
 * @see ServiceLoader
 * @since 1.2
 */
public abstract class IdentifierFactory {

    private static final Logger logger = LoggerFactory.getLogger(IdentifierFactory.class);
    private static final IdentifierFactory INSTANCE;

    static {
        logger.debug("Looking for IdentifierFactory implementation using the context class loader");
        IdentifierFactory factory = locateFactories(Thread.currentThread().getContextClassLoader(), "Context");
        if (factory == null) {
            logger.debug("Looking for IdentifierFactory implementation using the IdentifierFactory class loader.");
            factory = locateFactories(IdentifierFactory.class.getClassLoader(), "IdentifierFactory");
        }
        if (factory == null) {
            factory = new DefaultIdentifierFactory();
            logger.debug("Using default UUID-based IdentifierFactory");
        } else {
            logger.info("Found custom IdentifierFactory implementation: {}", factory.getClass().getName());
        }
        INSTANCE = factory;
    }

    private static IdentifierFactory locateFactories(ClassLoader classLoader, String classLoaderName) {
        IdentifierFactory found = null;
        Iterator<IdentifierFactory> services = ServiceLoader.load(IdentifierFactory.class, classLoader).iterator();
        if (services.hasNext()) {
            logger.debug("Found IdentifierFactory implementation using the {} Class Loader", classLoaderName);
            found = services.next();
            if (services.hasNext()) {
                logger.warn("More than one IdentifierFactory implementation was found using the {} Class Loader. This may result in different selections being made after restart of the application.", classLoaderName);
            }
        }
        return found;
    }

    /**
     * Returns an instance of the IdentifierFactory discovered on the classpath. This class uses the {@link
     * ServiceLoader} mechanism to find implementations. If none are found, it defaults to an implementation that
     * provides randomly chosen {@code java.util.UUID}s.
     *
     * @return the IdentifierFactory implementation found on the classpath.
     *
     * @see ServiceLoader
     */
    public static IdentifierFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Generates a unique identifier for use by Entities (generally the Aggregate Root) and Events. The implementation
     * may choose whatever strategy it sees fit, as long as the chance of a duplicate identifier is acceptable to the
     * application.
     *
     * @return a String representation of a unique identifier
     */
    public abstract String generateIdentifier();
}
