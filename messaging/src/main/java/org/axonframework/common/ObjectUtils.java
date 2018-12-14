/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.common;

import java.util.function.Supplier;

/**
 * Miscellaneous object utility methods
 */
public abstract class ObjectUtils {

    private ObjectUtils() {
        // prevent instantiation
    }

    /**
     * Returns the given instance, if not {@code null}, or otherwise the value provided by {@code defaultProvider}.
     *
     * @param instance        The value to return, if not {@code null}
     * @param defaultProvider To provide the value, when {@code instance} is {@code null}
     * @param <T>             The type of value to return
     * @return {@code instance} if not {@code null}, otherwise the value provided by {@code defaultProvider}
     */
    public static <T> T getOrDefault(T instance, Supplier<T> defaultProvider) {
        if (instance == null) {
            return defaultProvider.get();
        }
        return instance;
    }

    /**
     * Returns the given instance, if not {@code null}, or otherwise the given {@code defaultValue}.
     *
     * @param instance        The value to return, if not {@code null}
     * @param defaultValue The value, when {@code instance} is {@code null}
     * @param <T>             The type of value to return
     * @return {@code instance} if not {@code null}, otherwise {@code defaultValue}
     */
    public static <T> T getOrDefault(T instance, T defaultValue) {
        if (instance == null) {
            return defaultValue;
        }
        return instance;
    }
    /**
     * Returns the given instance, if not {@code null} or of zero length, or otherwise the given {@code defaultValue}.
     *
     * @param instance        The value to return, if not {@code null}
     * @param defaultValue The value, when {@code instance} is {@code null}
     * @param <T>             The type of value to return
     * @return {@code instance} if not {@code null}, otherwise {@code defaultValue}
     */
    public static <T extends CharSequence> T getNonEmptyOrDefault(T instance, T defaultValue) {
        if (instance == null || instance.length() == 0) {
            return defaultValue;
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> nullSafeTypeOf(T instance) {
        if (instance == null) {
            return (Class<T>) Void.class;
        }
        return (Class<T>) instance.getClass();
    }

    /**
     * Gets number of millis which are remaining of current deadline to be reached by {@link
     * System#currentTimeMillis()}. If deadline is passed, 0 will be returned.
     *
     * @param deadline deadline to be met
     * @return number of millis to deadline
     */
    public static long getRemainingOfDeadline(long deadline) {
        long leftTimeout = deadline - System.currentTimeMillis();
        leftTimeout = leftTimeout < 0 ? 0 : leftTimeout;
        return leftTimeout;
    }
}
