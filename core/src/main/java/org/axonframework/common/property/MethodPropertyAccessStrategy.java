package org.axonframework.common.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static java.lang.String.format;

public abstract class MethodPropertyAccessStrategy extends PropertyAccessStrategy {
    private static final Logger logger = LoggerFactory.getLogger(BeanPropertyAccessStrategy.class);

    @Override
    public Getter getterFor(Class<?> targetClass, String property) {
        String methodName = getterName(property);
        Method method = null;
        try {
            method = targetClass.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            logger.debug(format(
                    "Not found method with name '%s' in %s", methodName, targetClass.getName()), e);
        }
        return method == null ? null : new MethodGetter(method, property) {
        };
    }

    protected abstract String getterName(String property);
}
