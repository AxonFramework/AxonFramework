package org.axonframework.axonserver.connector.query.subscription;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;

import java.lang.reflect.Type;

/**
 * Class for internal use to ensure compatibility of Axon Framework with the new connection API, which does not rely
 * on response types to match handlers.
 *
 * @param <U> The declared response type
 */
class IgnoredResponseType<U> implements ResponseType<U> {

    private final Class<U> declaredType;

    public IgnoredResponseType(Class<U> declaredType) {
        this.declaredType = declaredType;
    }

    @Override
    public boolean matches(Type responseType) {
        return true;
    }

    @Override
    public Class<U> responseMessagePayloadType() {
        return declaredType;
    }

    @Override
    public Class<?> getExpectedResponseType() {
        return Object.class;
    }

    @Override
    public ResponseType<?> forSerialization() {
        // should never be serialized, but just in case
        return ResponseTypes.instanceOf(Object.class);
    }
}
