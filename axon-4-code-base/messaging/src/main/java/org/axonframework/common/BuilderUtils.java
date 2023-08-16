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

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Utility class containing reusable functionality for implementing the Builder Pattern in (infrastructure) components.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public abstract class BuilderUtils {

    private BuilderUtils() {
        // Utility class
    }

    /**
     * Assert that the given {@code value} will result to {@code true} through the {@code assertion} {@link Predicate}.
     * If not, an {@link AxonConfigurationException} is thrown containing the provided {@code exceptionMessage}.
     *
     * @param value            a {@code T} specifying the value to assert
     * @param assertion        a {@link Predicate} to test {@code value} against
     * @param exceptionMessage The message for the exception.
     * @param <T>              a generic specifying the type of the {@code value}, which is the input for the {@code
     *                         assertion}
     * @throws AxonConfigurationException if the {@code value} asserts to {@code false} by the {@code assertion}
     */
    public static <T> void assertThat(T value,
                                      Predicate<T> assertion,
                                      String exceptionMessage) throws AxonConfigurationException {
        Assert.assertThat(value, assertion, () -> new AxonConfigurationException(exceptionMessage));
    }

    /**
     * Assert that the given {@code value} is non null. If not, an {@link AxonConfigurationException} is thrown
     * containing the provided {@code exceptionMessage}.
     *
     * @param value            a {@code T} specifying the value to assert
     * @param exceptionMessage The message for the exception.
     * @param <T>              a generic specifying the type of the {@code value}, which is the input for the {@code
     *                         assertion}
     * @throws AxonConfigurationException if the {@code value} equals {@code null}
     */
    public static <T> void assertNonNull(T value, String exceptionMessage) throws AxonConfigurationException {
        assertThat(value, Objects::nonNull, exceptionMessage);
    }

    /**
     * Assert that the given {@code value} is positive, meaning greater than, or equal to, zero. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {code exceptionMessage}.
     *
     * @param i                the value to assert
     * @param exceptionMessage the message for the exception
     */
    public static void assertPositive(int i, String exceptionMessage) {
        assertThat(i, number -> number >= 0, exceptionMessage);
    }

    /**
     * Assert that the given {@code value} is positive, meaning greater than, or equal to, zero. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {code exceptionMessage}.
     *
     * @param l                the value to assert
     * @param exceptionMessage the message for the exception
     */
    public static void assertPositive(long l, String exceptionMessage) {
        assertThat(l, number -> number >= 0, exceptionMessage);
    }

    /**
     * Assert that the given {@code value} is strictly positive, meaning greater than zero. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {code exceptionMessage}.
     *
     * @param i                the value to assert
     * @param exceptionMessage the message for the exception.
     */
    public static void assertStrictPositive(int i, String exceptionMessage) {
        assertThat(i, number -> number > 0, exceptionMessage);
    }

    /**
     * Assert that the given {@code value} is strictly positive, meaning greater than zero. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {code exceptionMessage}.
     *
     * @param l                the value to assert
     * @param exceptionMessage the message for the exception.
     */
    public static void assertStrictPositive(long l, String exceptionMessage) {
        assertThat(l, number -> number > 0L, exceptionMessage);
    }

    /**
     * Assert that the given {@code string} is not null and does not equal an empty String. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {@code exceptionMessage}.
     *
     * @param string           the value to assert
     * @param exceptionMessage the message for the exception.
     */
    public static void assertNonEmpty(String string, String exceptionMessage) {
        assertThat(string, StringUtils::nonEmptyOrNull, exceptionMessage);
    }
    /**
     * Assert that the given {@code string} does not equals an empty String. If not, an {@link
     * AxonConfigurationException} is thrown containing the provided {@code exceptionMessage}.
     *
     * @param string           the value to assert
     * @param exceptionMessage the message for the exception.
     */
    public static void assertNonBlank(String string, String exceptionMessage) {
        assertThat(string, StringUtils::nonEmpty, exceptionMessage);
    }
}
