package org.axonframework.common.annotation;

import java.lang.reflect.Method;

public class UnifiedStylePropertyAccessor
        extends AbstractPropertyAccessor {

    @Override
    public String methodName(String propertyName) {
        return propertyName;
    }
}
