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

package org.axonframework.common;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Miscellaneous object utility methods.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public abstract class ObjectUtils {

    private ObjectUtils() {
        // Utility class
    }

    /**
     * Returns the given instance, if not {@code null}, or otherwise the value provided by {@code defaultProvider}.
     *
     * @param instance        the value to return, if not {@code null}
     * @param defaultProvider to provide the value, when {@code instance} is {@code null}
     * @param <T>             the type of value to return
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
     * @param instance     the value to return, if not {@code null}
     * @param defaultValue the value, when {@code instance} is {@code null}
     * @param <T>          the type of value to return
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
     * @param instance     the value to return, if not {@code null}
     * @param defaultValue the value, when {@code instance} is {@code null}
     * @param <T>          the type of value to return
     * @return {@code instance} if not {@code null}, otherwise {@code defaultValue}
     */
    public static <T extends CharSequence> T getNonEmptyOrDefault(T instance, T defaultValue) {
        if (instance == null || instance.length() == 0) {
            return defaultValue;
        }
        return instance;
    }

    /**
     * Returns the result of the given {@code valueProvider} by ingesting the given {@code instance}, <em>if</em> the
     * {@code instance} is not {@code null}. If it is, the {@code defaultValue} is returned.
     *
     * @param instance      the value to verify if it is not {@code null}. If it isn't, the given {@code valueProvider}
     *                      will be invoked with this object
     * @param valueProvider the function to return the result of by ingesting the {@code instance} if it is not null
     * @param defaultValue  the value to return if the given {@code instance} is {@code null}
     * @param <I>           the type of the {@code instance} to verify and use by the {@code valueProvider}
     * @param <T>           the type of value to return
     * @return the output of {@code valueProvider} by ingesting {@code instance} if it is not {@code null}, otherwise
     * the {@code defaultValue}
     */
    public static <I, T> T getOrDefault(I instance, Function<I, T> valueProvider, T defaultValue) {
        return instance != null ? valueProvider.apply(instance) : defaultValue;
    }

    /**
     * Returns the type of the given {@code instance}, <em>if</em> it is not {@code null}. If it is {@code null}, {@link
     * Void#getClass()} will be returned.
     *
     * @param instance the object to return the type for
     * @param <T>      the generic type of the {@link Class} to return
     * @return the type of the given {@code instance} if it is not {@code null}, otherwise {@link Void#getClass()}
     */
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

    /**
     * Wraps the given {@code supplier} to ensure that the same instance is returned on multiple consecutive
     * invocations. While it guarantees that the same instance is returned, concurrent access may cause given
     * {@code supplier} to be invoked more than once.
     *
     * @param supplier The supplier to provide the instance to return
     * @param <T>      The type of object supplied
     *
     * @return a supplier that returns the same instance
     */
    public static <T> Supplier<T> sameInstanceSupplier(Supplier<T> supplier) {
        AtomicReference<T> instanceRef = new AtomicReference<>();
        // Using the AtomicReference ensures the lock is only created once for the supplier's invocations.
        return () -> instanceRef.updateAndGet(current -> getOrDefault(current, supplier));
    }
}
