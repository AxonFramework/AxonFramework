package org.axonframework.queryhandling;

/**
 * Utility class containing static methods to obtain instances of {@link org.axonframework.queryhandling.ResponseType}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public abstract class ResponseTypes {

    /**
     * Specify the desire to retrieve a single instance of type {@code T} when performing a query.
     *
     * @param type the {@code T} which is expected to be the response type
     * @param <T>  the generic type of the instantiated {@link org.axonframework.queryhandling.ResponseType}
     * @return a {@link org.axonframework.queryhandling.ResponseType} specifying the desire to retrieve a single
     * instance of type {@code T}
     */
    public static <T> ResponseType<T> instanceOf(Class<T> type) {
        return new InstanceResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve a list of instances of type {@code T} when performing a query.
     *
     * @param type the {@code T} which is expected to be the response type
     * @param <T>  the generic type of the instantiated {@link org.axonframework.queryhandling.ResponseType}
     * @return a {@link org.axonframework.queryhandling.ResponseType} specifying the desire to retrieve a list of
     * instances of type {@code T}
     */
    public static <T> ResponseType<T> listOf(Class<T> type) {
        return new ListResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve a page of instances of type {@code T} when performing a query.
     *
     * @param type the {@code T} which is expected to be the response type
     * @param <T>  the generic type of the instantiated {@link org.axonframework.queryhandling.ResponseType}
     * @return a {@link org.axonframework.queryhandling.ResponseType} specifying the desire to retrieve a page of
     * instances of type {@code T}
     */
    public static <T> ResponseType<T> pageOf(Class<T> type) {
        return new PageResponseType<>(type);
    }

    private ResponseTypes() {
        // Utility class
    }
}
