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

package org.axonframework.common.annotation;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Abstract Factory that provides access to all ParameterResolverFactory implementations.
 * <p/>
 * Application developers may provide custom ParameterResolverFactory implementations using the ServiceLoader
 * mechanism. To do so, place a file called <code>org.axonframework.common.annotation.ParameterResolverFactory</code>
 * in the <code>META-INF/services</code> folder. In this file, place the fully qualified class names of all available
 * implementations.
 * <p/>
 * The factory implementations must be public, non-abstract, have a default public constructor and extend the
 * ParameterResolverFactory class.
 *
 * @author Allard Buijze
 * @see ServiceLoader
 * @see ServiceLoader#load(Class)
 * @since 2.0
 */
public abstract class ParameterResolverFactory {

    private static final ServiceLoader<ParameterResolverFactory> FACTORY_LOADER =
            ServiceLoader.load(ParameterResolverFactory.class);

    private static final List<ParameterResolverFactory> RESOLVERS = new CopyOnWriteArrayList<ParameterResolverFactory>();
    private static final DefaultParameterResolverFactory DEFAULT_FACTORY = new DefaultParameterResolverFactory();
    private static final Set<ChangeListener> changeListeners = new CopyOnWriteArraySet<ChangeListener>();

    static {
        for (ParameterResolverFactory factory : FACTORY_LOADER) {
            RESOLVERS.add(factory);
        }
    }

    /**
     * Registers a ParameterResolverFactory at runtime. Annotated handlers that have already been inspected *may* not
     * be able to use the newly added factory.
     * <p/>
     * All registered ChangeListeners are notified of the registration
     *
     * @param factory The factory to register
     */
    public static void registerFactory(ParameterResolverFactory factory) {
        RESOLVERS.add(factory);
        notifyChangeListeners();
    }

    /**
     * Unregisters a ParameterResolverFactory at runtime. Annotated handlers that have already been inspected *may* not
     * be affected by the removal of the parameter resolver.
     * <p/>
     * All registered ChangeListeners are notified of the registration if the given <code>factory</code> was registered
     * prior to this method invocation.
     *
     * @param factory The factory to unregister
     */
    public static void unregisterFactory(ParameterResolverFactory factory) {
        if (RESOLVERS.remove(factory)) {
            notifyChangeListeners();
        }
    }

    private static void notifyChangeListeners() {
        for (ChangeListener listener : changeListeners) {
            listener.onChange();
        }
    }

    /**
     * Iterates over all known ParameterResolverFactory implementations to create a ParameterResolver instance for the
     * given parameters. The ParameterResolverFactories invoked in the order they are found on the classpath. The first
     * to provide a suitable resolver will be used. The DefaultParameterResolverFactory is always the last one to be
     * inspected.
     *
     * @param memberAnnotations    annotations placed on the member (e.g. method)
     * @param parameterType        the parameter type to find a resolver for
     * @param parameterAnnotations annotations places on the parameter
     * @param isPayloadParameter   whether the parameter to resolve represents a payload parameter
     * @return a suitable ParameterResolver, or <code>null</code> if none is found
     */
    public static ParameterResolver findParameterResolver(Annotation[] memberAnnotations, Class<?> parameterType,
                                                          Annotation[] parameterAnnotations,
                                                          boolean isPayloadParameter) {
        ParameterResolver resolver = null;
        Iterator<ParameterResolverFactory> factories = RESOLVERS.iterator();
        while (resolver == null && factories.hasNext()) {
            final ParameterResolverFactory factory = factories.next();
            if (!isPayloadParameter || factory.supportsPayloadResolution()) {
                resolver = factory.createInstance(memberAnnotations, parameterType, parameterAnnotations);
            }
        }
        if (resolver == null) {
            resolver = DEFAULT_FACTORY.createInstance(memberAnnotations, parameterType, parameterAnnotations);
        }
        return resolver;
    }

    /**
     * Creates a ParameterResolver that resolves the payload of a given message when it is assignable to the given
     * <code>parameterType</code>.
     *
     * @param parameterType The type of parameter to create a resolver for
     * @return a ParameterResolver that resolves the payload of a given message
     */
    public static ParameterResolver createPayloadResolver(Class<?> parameterType) {
        return DEFAULT_FACTORY.newPayloadResolver(parameterType);
    }

    /**
     * Indicates whether this resolver may be used to resolve the payload parameter of an annotated handler method.
     *
     * @return whether this factory provides Parameter Resolvers that support payload parameters
     */
    public abstract boolean supportsPayloadResolution();

    /**
     * If available, creates a ParameterResolver instance that can provide a parameter of type
     * <code>parameterType</code> for a given message.
     * <p/>
     * If the ParameterResolverFactory cannot provide a suitable ParameterResolver, returns <code>null</code>.
     *
     * @param memberAnnotations    annotations placed on the member (e.g. method)
     * @param parameterType        the parameter type to find a resolver for
     * @param parameterAnnotations annotations placed on the parameter
     * @return a suitable ParameterResolver, or <code>null</code> if none is found
     */
    protected abstract ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                        Annotation[] parameterAnnotations);

    /**
     * Registers a listener that is notified when the ParameterResolverFactory instances are registered or
     * unregistered. Generally, when caching the results of the ParameterResolverFactories, the component managing the
     * cache should listen to changes to invalidate the cache.
     *
     * @param listener The listener to notify when state has changed
     */
    public static void registerChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Interface of the listener that listens to state changes in the ParameterResolverFactories available to the
     * application.
     */
    static interface ChangeListener {

        /**
         * Invoked when a ParameterResolverFactory has been registered or unregistered.
         */
        void onChange();
    }
}
