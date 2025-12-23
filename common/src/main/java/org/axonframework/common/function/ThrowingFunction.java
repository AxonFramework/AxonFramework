package org.axonframework.common.function;

/**
 * Functional interface for operations which may throw a checked exception.
 *
 * @param <T> the input type of the function
 * @param <R> the result type of the function
 * @param <X> the exception type the function may throw
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface ThrowingFunction<T, R, X extends Exception> {

    /**
     * Applies the function to the given {@code T}.
     *
     * @param input the input of type {@code T}
     * @return the result of applying the function
     * @throws X when the function failed with an exception of type {@code X}
     */
    R apply(T input) throws X;
}