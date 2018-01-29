package org.axonframework.queryhandling.responsetypes;

import java.util.List;

/**
 * Utility class containing static methods to obtain instances of
 * {@link org.axonframework.queryhandling.responsetypes.ResponseType}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public abstract class ResponseTypes {

    /**
     * Specify the desire to retrieve a single instance of type {@code R} when performing a query.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated
     *             {@link org.axonframework.queryhandling.responsetypes.ResponseType}
     * @return a {@link org.axonframework.queryhandling.responsetypes.ResponseType} specifying the desire to retrieve a
     * single instance of type {@code R}
     */
    public static <R> ResponseType<R> instanceOf(Class<R> type) {
        return new InstanceResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve a collection of instances of type {@code R} when performing a query.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated
     *             {@link org.axonframework.queryhandling.responsetypes.ResponseType}
     * @return a {@link org.axonframework.queryhandling.responsetypes.ResponseType} specifying the desire to retrieve a
     * collection of instances of type {@code R}
     */
    public static <R> ResponseType<List<R>> multipleInstancesOf(Class<R> type) {
        return new MultipleInstancesResponseType<>(type);
    }

    private ResponseTypes() {
        // Utility class
    }
}
