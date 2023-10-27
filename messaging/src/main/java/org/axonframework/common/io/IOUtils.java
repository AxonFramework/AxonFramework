/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.io;

import java.nio.charset.Charset;

/**
 * Utility methods for IO operations.
 *
 * @author Allard Buijze
 * @author Knut-Olav Hoven
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
     * Closes any AutoCloseable object, while suppressing any IOExceptions it will generate. The given
     * {@code closeable} may be {@code null}, in which case nothing happens.
     *
     * @param closeable the object to be closed
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) { // NOSONAR - empty catch block on purpose
                // ignore
            }
        }
    }

    /**
     * Closes any object if that object implements {@link AutoCloseable}, while suppressing any IOExceptions it will
     * generate. The given {@code closeable} may be {@code null}, in which case nothing happens.
     *
     * @param closeable the object to be closed
     */
    public static void closeQuietlyIfCloseable(Object closeable) {
        if (closeable instanceof AutoCloseable) {
            closeQuietly((AutoCloseable) closeable);
        }
    }

}
