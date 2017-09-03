package org.axonframework.serialization.compression;

import java.io.IOException;

public class NotCompressedException extends IOException {

    private static final long serialVersionUID = 5340375531489943710L;

    public NotCompressedException(String message) {
        super(message);
    }

    public NotCompressedException(String message, Throwable cause) {
        super(message, cause);
    }
}
