package org.axonframework.common.property;


import org.axonframework.common.AxonConfigurationException;

/**
 * Exception indicating that a predefined property is not accessible. Generally, this means that the property does not
 * conform to the accessibility requirements of the accessor.
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class PropertyAccessException extends AxonConfigurationException {

    private static final long serialVersionUID = -1360531453606316133L;

    /**
     * Initializes the PropertyAccessException with given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the cause
     * @param cause   The underlying cause of the exception
     */
    public PropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
