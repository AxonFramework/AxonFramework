package org.axonframework.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;


public abstract class MethodPropertyAccessor extends AbstractPropertyAccessor {

    public MethodPropertyAccessor(String aProperty) {
        super(aProperty);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, V> V getValue(T target) {
        try {
            Method getter = target.getClass().getMethod(getter());
            ensureAccessible(getter);
            return (V) getter.invoke(target);
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. " +
                    "Property methods should be accessible",
                    property, getter(), target.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. " +
                    "Property methods should not throw exceptions." ,
                    property, getter(), target.getClass().getName()), e);
        } catch (NoSuchMethodException e) {
            throw new PropertyAccessException(format(
                    "Failed to set value of '%s' using method '%s()'. " +
                    "%s has no such method",
                    property, setter(), target.getClass().getName()), e);
        }
    }

    @Override
    public <T, V> void setValue(V value, T target) {
        try {
            Method setter = target.getClass().getMethod(setter(), value.getClass());
            ensureAccessible(setter);
            setter.invoke(target, value);
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(format(
                    "Failed to set value of '%s' using method '%s(%s)' of '%s'. " +
                    "Property methods should be accessible",
                    property, setter(), value.getClass().getName(), target.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            throw new PropertyAccessException(format(
                    "Failed to set value of '%s' using method '%s(%s)' of '%s'. " +
                    "Property methods should not throw exceptions",
                    property, setter(), value.getClass().getName(), target.getClass().getName()), e);
        } catch (NoSuchMethodException e) {
            throw new PropertyAccessException(format(
                    "Failed to set value of '%s' using method '%s(%s)'. " +
                    "%s has no such method",
                    property, setter(), value.getClass().getName(), target.getClass().getName()));
        }
    }

    /** Returns getter name for given property name */
    public abstract String getter();

    /** Returns getter name for given property name */
    public abstract String setter();
}
