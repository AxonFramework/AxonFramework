package org.axonframework.queryhandling.responsetypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Type;

/**
 * A {@link org.axonframework.queryhandling.responsetypes.ResponseType} implementation that will match with query
 * handlers which return a single instance of the expected response type. If matching succeeds, the
 * {@link ResponseType#convert(Object)} function will be called, which will cast the query handler it's response to
 * {@code R}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Steven van Beelen
 * @since 3.2
 */
public class InstanceResponseType<R> extends AbstractResponseType<R> {

    /**
     * Instantiate a {@link org.axonframework.queryhandling.responsetypes.InstanceResponseType} with the given
     * {@code expectedResponseType} as the type to be matched against and to which the query response should be converted
     * to.
     *
     * @param expectedResponseType the response type which is expected to be matched against and returned
     */
    @JsonCreator
    public InstanceResponseType(@JsonProperty("expectedResponseType") Class<R> expectedResponseType) {
        super(expectedResponseType);
    }

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with this implementation its responseType
     * {@code R}.
     * Will return true if the expected type is assignable to the response type, taking generic types into account.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if the response type is assignable to the expected type, taking generic types into account
     */
    @Override
    public boolean matches(Type responseType) {
        Type unwrapped = unwrapIfTypeFuture(responseType);
        return isGenericAssignableFrom(unwrapped) || isAssignableFrom(unwrapped);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<R> responseMessagePayloadType() {
        return (Class<R>) expectedResponseType;
    }
}
