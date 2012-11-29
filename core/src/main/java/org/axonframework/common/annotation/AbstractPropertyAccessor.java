package org.axonframework.common.annotation;

import java.lang.reflect.Method;

public abstract class AbstractPropertyAccessor implements PropertyAccessor {
    @Override
    public Method methodFor(String property, Class<?> inClass) throws NoSuchMethodException {
        return inClass.getMethod(methodName(property));
    }
}
