/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.test;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Error indication that an Assertion failed during a test case. The message of the error contains detailed information
 * about the failed assertion.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class AxonAssertionError extends AssertionError {

    private static final long serialVersionUID = 3731933425096971345L;

    /**
     * Create a new error instance using the given {@code detailMessage}.
     *
     * @param detailMessage A detailed description of the failed assertion.
     */
    public AxonAssertionError(@Nonnull String detailMessage) {
        super(detailMessage);
        StackTraceElement[] original = getStackTrace();
        setStackTrace(cleanStackTrace(original));
    }

    /**
     * Create a new error instance using the given {@code cause} and {@code detailMessage}.
     *
     * @param detailMessage A detailed description of the failed assertion.
     * @param cause         The cause of the error.
     */
    public AxonAssertionError(@Nonnull String detailMessage, @Nonnull Throwable cause) {
        super(Objects.requireNonNull(detailMessage), Objects.requireNonNull(cause));
        StackTraceElement[] original = getStackTrace();
        setStackTrace(cleanStackTrace(original));
    }

    private StackTraceElement[] cleanStackTrace(StackTraceElement[] original) {
        int ignoreCount = 0;
        for (StackTraceElement element : original) {
            if (element.getClassName().startsWith("org.axonframework.test")) {
                ignoreCount++;
            } else {
                break;
            }
        }
        return Arrays.copyOfRange(original, ignoreCount, original.length);
    }
}
