package org.axonframework.common.annotation;

import org.axonframework.common.AxonConfigurationException;

import static java.lang.String.format;

public abstract class PropertyAccessorFactory {
    private PropertyAccessorFactory() {
        // not intended to be instantiated by client code
    }

    static public PropertyAccessor createFor(Class<? extends PropertyAccessor> cls) {
        try {
            return cls.newInstance();
        } catch (InstantiationException e) {
            throw new AxonConfigurationException(
                    format("Could not instantiate %s. Check that it has no-arg constructor", cls.getSimpleName()), e.getCause());
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException(
                    format("Class constructor of %s is not accessible.", cls.getSimpleName()), e.getCause());

        }
    }

}
