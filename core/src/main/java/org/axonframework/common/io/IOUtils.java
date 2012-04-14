package org.axonframework.common.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Utility methods for IO operations.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class IOUtils {

    /**
     * Represents the UTF-8 character set.
     */
    public static final Charset UTF8 = Charset.forName("UTF-8");

    private IOUtils() {
    }

    /**
     * Closes any Closable object, while suppressing any IOExceptions it will generate. The given
     * <code>closeable</code> may be <code>null</code>, in which case nothing happens.
     *
     * @param closeable the object to be closed
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) { // NOSONAR - empty catch block on purpose
                // ignore
            }
        }
    }
}
