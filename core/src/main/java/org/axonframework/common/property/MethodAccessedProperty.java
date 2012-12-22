package org.axonframework.common.property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Property implementation that invokes a method to obtain a value of a property for a given instance.
 *
 * @param <T> The type of object defining this property
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class MethodAccessedProperty<T> implements Property<T> {

    private final Method method;
    private final String property;

    /**
     * Initialize a reader that uses given <code>accessorMethod</code> to access a property with given
     * <code>propertyName</code>.
     *
     * @param accessorMethod The method providing the property value
     * @param propertyName   The name of the property
     */
    public MethodAccessedProperty(Method accessorMethod, String propertyName) {
        property = propertyName;
        method = ensureAccessible(accessorMethod);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getValue(T target) {
        try {
            return (V) method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. Property methods should be accessible",
                    property, method.getName(), target.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. "
                            + "Property methods should not throw exceptions.",
                    property, method.getName(), target.getClass().getName()), e);
        }
    }
}
