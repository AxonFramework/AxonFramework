package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 * @param <R>
 * @author Steven van Beelen
 * @since 3.2
 */
public class InstanceResponseType<R> implements ResponseType<R> {

    private final Class<?> expectedResponseType;

    public InstanceResponseType(Class<?> expectedResponseType) {
        this.expectedResponseType = expectedResponseType;
    }

    @Override
    public boolean matches(Type responseType) {
        return false;
    }

    @Override
    public R convert(Object response) {
        return null;
    }
}
