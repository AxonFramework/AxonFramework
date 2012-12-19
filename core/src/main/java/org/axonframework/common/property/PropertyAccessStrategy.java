package org.axonframework.common.property;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Abstract Strategy that provides access to all PropertyAccessStrategy implementations.
 * <p/>
 * Application developers may provide custom PropertyAccessStrategy implementations using the ServiceLoader
 * mechanism. To do so, place a file called <code>org.axonframework.common.property.PropertyAccessStrategy</code>
 * in the <code>META-INF/services</code> folder. In this file, place the fully qualified class names of all available
 * implementations.
 * <p/>
 * The factory implementations must be public, non-abstract, have a default public constructor and extend the
 * PropertyAccessStrategy class.
 *
 * @see java.util.ServiceLoader
 * @see java.util.ServiceLoader#load(Class)
 * @since 2.0
 */
public abstract class PropertyAccessStrategy {

    private static final ServiceLoader<PropertyAccessStrategy> LOADER =
            ServiceLoader.load(PropertyAccessStrategy.class);

    private static final List<PropertyAccessStrategy> STRATEGIES = new CopyOnWriteArrayList<PropertyAccessStrategy>();
    private static final PropertyAccessStrategy DEFAULT = new BeanPropertyAccessStrategy();

    static {
        for (PropertyAccessStrategy factory : LOADER) {
            STRATEGIES.add(factory);
        }
    }

    /**
     * Registers a PropertyAccessStrategy implementation at runtime.
     * Annotated handlers that have already been inspected will not be able to use the newly added strategy.
     *
     * @param strategy implementation to register
     */
    public static void register(PropertyAccessStrategy strategy) {
        STRATEGIES.add(strategy);
    }

    /**
     * Iterates over all known {@link PropertyAccessStrategy} implementations to create a {@link Getter}
     * instance for the given parameters. Strategies invoked in the order they are found on the classpath.
     * The first to provide a suitable getter will be used. {@link BeanPropertyAccessStrategy} is always the last one
     * to be inspected.
     *
     * @param targetClass class that contains property
     * @param property    name of the property to create getter for
     * @return suitable {@link Getter}, or <code>null</code> if none is found
     */
    public static Getter getter(Class<?> targetClass, String property) {
        Getter getter = null;
        Iterator<PropertyAccessStrategy> strategies = STRATEGIES.iterator();
        while (getter == null && strategies.hasNext()) {
            getter = strategies.next().getterFor(targetClass, property);
        }
        if (getter == null) {
            getter = DEFAULT.getterFor(targetClass, property);
        }

        return getter;
    }


    protected abstract Getter getterFor(Class<?> targetClass, String property);

}

