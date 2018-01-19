package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 * Specifies the expected response type required when performing a query through the
 * {@link org.axonframework.queryhandling.QueryBus}/{@link org.axonframework.queryhandling.QueryGateway}. It is
 * generally thus required to provided an instance of {@link ResponseType} when performing a query.
 * By wrapping the response type as a generic {@code T}, we can easily
 * service that expected type as a single instance, a list, a page etc., based on the selected implementation.
 * </p>
 * It is in charge of matching the response type of a query handler with the given generic {@code T}.
 * If this match returns true, it signals the found query handler can handle the intended query.
 * As a follow up, the response retrieved from a query handler should move through the convert function to guarantee the
 * right response type is returned.
 *
 * @param <T> the generic type of this {@link ResponseType} to be matched and converted.
 * @author Steven van Beelen
 * @since 3.2
 */
public interface ResponseType<T> {

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with this response its type {@code T}.
     * Will return true if a response can be converted based on the given {@code type} and false if it cannot.
     *
     * @param type the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if a response can be converted based on the given {@code type} and false if it cannot
     */
    boolean matches(Type type);

    /**
     * Converts the given {@code response} of type {@link java.lang.Object} into the type {@code T} of this
     * {@link ResponseType} instance. Should only be called if {@link ResponseType#matches(Type)} returns true.
     *
     * @param response the {@link java.lang.Object} to convert into {@code T}
     * @return a {@code response} of type {@code T}
     */
    @SuppressWarnings("unchecked")
    default T convert(Object response) {
        return (T) response;
    }
}
