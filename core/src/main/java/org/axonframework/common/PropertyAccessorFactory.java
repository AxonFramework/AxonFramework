package org.axonframework.common;


public abstract class PropertyAccessorFactory {

    private PropertyAccessorFactory() {
        // not intended to be instantiated by client code
    }

    static public PropertyAccessor createFor(String property) {
        return new BeanStylePropertyAccessor(property);
    }

}
