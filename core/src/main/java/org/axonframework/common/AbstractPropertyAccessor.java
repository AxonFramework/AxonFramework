package org.axonframework.common;

public abstract class AbstractPropertyAccessor implements PropertyAccessor {

    public AbstractPropertyAccessor(String aProperty) {
        property = aProperty;
    }

    protected final String property;
}
