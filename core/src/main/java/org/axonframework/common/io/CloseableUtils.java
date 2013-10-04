package org.axonframework.common.io;

import java.io.Closeable;

public class CloseableUtils {
    public static void closeIfCloseable(Object candidate) {
        if (candidate instanceof Closeable) {
            IOUtils.closeQuietly((Closeable) candidate);
        }
    }
}
