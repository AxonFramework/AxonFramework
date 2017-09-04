package org.axonframework.serialization.compression;

import java.io.IOException;

/**
 */

/**
 * Exception thrown when serialized data is not compressed or can't be decompressed.
 *
 * @author Michael Willemse
 */
public class NotCompressedException extends IOException {

    private static final long serialVersionUID = 5340375531489943710L;

    /**
     * Initializes NotCompressedException with a {@code message}.
     * @param message The exception message.
     */
    public NotCompressedException(String message) {
        super(message);
    }

    /**
     * Initializes NotCompressedException with a {@code message} and {@code cause}.
     * @param message The exception message.
     * @param cause The exception cause.
     */
    public NotCompressedException(String message, Throwable cause) {
        super(message, cause);
    }
}
