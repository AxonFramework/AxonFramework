package org.axonframework.common.annotation;

import java.lang.reflect.Method;

/**
 * Extend {@link AbstractPropertyAccessor} instead of implementing this.
 */
public interface PropertyAccessor {
    /**
     * Returns method to access defined property
     * @param property name of property to access to
     * @param inClass class of object that contains property
     *
     * @throws NoSuchMethodException if method for property not found
     */
    public Method methodFor(String property, Class<?> inClass) throws NoSuchMethodException;

    /**
     * Returns method name for given property name
     * @param propertyName name of property to access to
     */
    public String methodName(String propertyName);
}
