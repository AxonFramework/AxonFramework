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

package org.axonframework.common;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for constructing objects using reflection.
 * <p>
 * Utility functions in this class do not create instances directly. Instead, they provide reusable functions that can
 * be used to construct a multitude of instances of the same type. It is advisable to cache this function for reuse.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ConstructorUtils {

    /**
     * Returns a function that constructs an instance of the given type using the zero-argument constructor. If the type
     * does not have a zero-argument constructor, an {@link IllegalArgumentException} is thrown.
     *
     * @param type The type of object to construct. Must have a zero-argument constructor.
     * @param <T>  The type of object to construct.
     * @return A function that constructs an instance of the given type using the zero-argument constructor.
     */
    public static <T> Supplier<T> getConstructorFunctionWithZeroArguments(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            ReflectionUtils.ensureAccessible(constructor);
            return () -> doConstructionWithOptionalArgument(type, null, constructor);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "No suitable constructor found for entity of type [%s] with zero arguments"
                            .formatted(type.getName()));
        }
    }

    /**
     * Returns a function that constructs an instance of the given type using the constructor that accepts an argument
     * of the given class, or alternatively using a zero-argument constructor. If neither is available, an
     * {@link IllegalArgumentException} is thrown.
     *
     * @param type          The type of object to construct. Must have a constructor that accepts an argument of the
     *                      given class, or a zero-argument constructor.
     * @param argumentClass The class of the argument to pass to the constructor.
     * @param <T>           The type of object to construct.
     * @param <A>           The type of the argument to pass to the constructor.
     * @return A function that constructs an instance of the given type using the constructor that accepts an argument
     * of the given class, or alternatively using a zero-argument constructor.
     */
    public static <T, A> Function<A, T> getConstructorFunctionWithOptionalArgumentClass(
            @Nonnull Class<T> type,
            @Nonnull Class<? extends A> argumentClass) {
        Constructor<T> constructor = getConstructorWithOptionalArgumentOfType(type, argumentClass);
        return arg -> doConstructionWithOptionalArgument(type, arg, constructor);
    }

    /**
     * Returns a function that constructs an instance of the given type using the constructor that accepts an argument
     * of the given instance's class, or alternatively using a zero-argument constructor. If neither is available, an
     * {@link IllegalArgumentException} is thrown.
     *
     * @param type     The type of object to construct. Must have a constructor that accepts an argument of the given
     *                 instance's class, or a zero-argument constructor.
     * @param argument The instance to pass to the constructor.
     * @param <T>      The type of object to construct.
     * @param <A>      The type of the argument to pass to the constructor.
     * @return A function that constructs an instance of the given type using the constructor that accepts an argument
     * of the given instance's class, or alternatively using a zero-argument constructor.
     */
    public static <T, A> Supplier<T> factoryForTypeWithOptionalArgumentInstance(
            @Nonnull Class<T> type,
            @Nonnull A argument
    ) {
        Constructor<T> constructor = getConstructorWithOptionalArgumentOfType(type, argument.getClass());
        return () -> doConstructionWithOptionalArgument(type, argument, constructor);
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> getConstructorWithOptionalArgumentOfType(@Nonnull Class<T> type,
                                                                               @Nonnull Class<?> argument) {
        return (Constructor<T>) Arrays
                .stream(type.getDeclaredConstructors())
                .filter(constructor -> constructorHasZeroOrExactlyThisArgument(constructor, argument))
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No suitable constructor found for entity of type [%s] with optional argument of type [%s]"
                                .formatted(type.getName(), argument.getName())));
    }

    private static boolean constructorHasZeroOrExactlyThisArgument(@Nonnull Constructor<?> constructor,
                                                                   @Nonnull Class<?> argument) {
        // Has no args, cool
        if (constructor.getParameterCount() == 0) {
            return true;
        }
        // We want a constructor that can accept the argument
        return constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isAssignableFrom(argument);
    }

    private static <T> T doConstructionWithOptionalArgument(Class<T> type,
                                                            Object argument,
                                                            Constructor<T> constructor) {
        try {
            ReflectionUtils.ensureAccessible(constructor);
            if (constructor.getParameterCount() == 0) {
                return constructor.newInstance();
            }
            return constructor.newInstance(argument);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("Error creating %s".formatted(type), e);
        }
    }

    private ConstructorUtils() {
        // Utility class
    }
}
