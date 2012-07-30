package org.axonframework.common.annotation;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract Factory that provides access to all ParameterResolverFactory implementations.
 * <p/>
 * Application developers may provide custom ParameterResolverFactory implementations using the ServiceLoader
 * mechanism. To do so, place a file called <code>org.axonframework.common.annotation.ParameterResolverFactory</code>
 * in
 * the
 * <code>META-INF/services</code> folder. In this file, place the fully qualified class names of all available
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

    static {
        for (ParameterResolverFactory factory : FACTORY_LOADER) {
            RESOLVERS.add(factory);
        }
    }

    /**
     * Registers a ParameterResolverFactory at runtime. Annotated handlers that have already been inspected will not be
     * able to use the newly added factory.
     *
     * @param factory The factory to register
     */
    public static void registerFactory(ParameterResolverFactory factory) {
        RESOLVERS.add(factory);
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
     * @param parameterAnnotations annotations places on the parameter
     * @return a suitable ParameterResolver, or <code>null</code> if none is found
     */
    protected abstract ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                        Annotation[] parameterAnnotations);
}
