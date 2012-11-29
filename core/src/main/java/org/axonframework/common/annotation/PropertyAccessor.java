package org.axonframework.common.annotation;

import java.lang.reflect.Method;

public interface PropertyAccessor {
    public Method methodFor(String property, Class<?> inClass) throws NoSuchMethodException;
    public String methodName(String propertyName);

}
