package org.axonframework.common.property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;


public class MethodGetter implements Getter {

    private final Method method;
    protected final String property;

    public MethodGetter(Method aMethod, String aProperty) {
        property = aProperty;
        method = aMethod;
        ensureAccessible(method);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getValue(Object target) {
        try {
            return (V) method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. " +
                    "Property methods should be accessible",
                    property, method.getName(), target.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. " +
                    "Property methods should not throw exceptions." ,
                    property, method.getName(), target.getClass().getName()), e);
        }
    }
}
