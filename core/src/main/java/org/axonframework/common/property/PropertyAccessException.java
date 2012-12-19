package org.axonframework.common.property;


import org.axonframework.common.AxonConfigurationException;

public class PropertyAccessException extends AxonConfigurationException {
    public PropertyAccessException(String message) {
        super(message);
    }

    public PropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
