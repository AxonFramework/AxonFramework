package org.axonframework.common.function;

/**
 * Functional interface for operations which may throw a checked exception.
 *
 * @param <T> the input type of the consumer
 * @param <X> the exception type the consumer may throw
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface ThrowingConsumer<T, X extends Exception> {

    /**
     * Accepts an input to perform an operation.
     *
     * @param input the input of type {@code T}
     * @throws X when the consumer failed with an exception of type {@code X}
     */
    void accept(T input) throws X;
}