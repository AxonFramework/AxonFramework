package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 *
 * @param <T>
 * @author Steven van Beelen
 * @since 3.2
 */
public class InstanceResponseType<T> implements ResponseType<T> {

    private final Class responseType;

    public InstanceResponseType(Class<?> responseType) {
        this.responseType = responseType;
    }

    @Override
    public boolean matches(Type type) {
        return false;
    }

}
