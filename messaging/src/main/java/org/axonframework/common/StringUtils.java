/*
 * Copyright (c) 2010-2022. Axon Framework
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

/**
 * Utility methods for when dealing with {@link String}s.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public abstract class StringUtils {

    private static final String EMPTY_STRING = "";

    private StringUtils() {
        // Utility class
    }

    /**
     * Validate whether the given {@link String} {@code s} is not {@code null} and not empty (where empty is defined as
     * {@code ""}).
     *
     * @param s the {@link String} to validate whether it is not {@code null} and not empty
     * @return {@code true} if the given {@link String} {@code s} is not {@code null} and not empty, {@code false}
     * otherwise
     */
    public static boolean nonEmptyOrNull(String s) {
        return Objects.nonNull(s) && !EMPTY_STRING.equals(s);
    }

    /**
     * Validate whether the given {@link String} {@code s} is {@code null} or not empty (where empty is defined as
     * {@code ""}).
     *
     * @param s The {@link String} to validate whether it is {@code null} or empty.
     * @return {@code true} if the given {@link String} {@code s} is not {@code null} and not empty, {@code false}
     * otherwise.
     */
    public static boolean emptyOrNull(String s) {
        return Objects.isNull(s) || EMPTY_STRING.equals(s);
    }

    /**
     * Validate whether the given {@link String} {@code s} not empty (where empty is defined as
     * {@code ""}).
     *
     * @param s the {@link String} to validate whether not empty
     * @return {@code true} if the given {@link String} {@code s} is not empty, {@code false}
     * otherwise
     */
    public static boolean nonEmpty(String s) {
        return !EMPTY_STRING.equals(s);
    }

    /**
     * Return the given {@code s}, with its first character lowercase.
     *
     * @param s The input string to adjust to a version with the first character as a lowercase.
     * @return The input string, with first character lowercase.
     */
    public static String lowerCaseFirstCharacterOf(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }
}

