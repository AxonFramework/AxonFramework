package org.axonframework.serializer;

import org.axonframework.common.AxonConfigurationException;

/**
 * Exception indicating that a conversion is required between to upcasters, but there is no converter capable of doing
 * the conversion.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CannotConvertBetweenTypesException extends AxonConfigurationException {

    private static final long serialVersionUID = 2707247238103378855L;

    /**
     * Initializes the exception with the given <code>message</code>.
     *
     * @param message The message describing the problem
     */
    public CannotConvertBetweenTypesException(String message) {
        super(message);
    }

    /**
     * Initializing the exception with given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the problem
     * @param cause   The original cause of the exception
     */
    public CannotConvertBetweenTypesException(String message, Throwable cause) {
        super(message, cause);
    }
}
