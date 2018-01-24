package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 *
 * @param <T>
 * @author Steven van Beelen
 * @since 3.2
 */
public class PageResponseType<T> implements ResponseType<Page<T>> {

    private final Type expectedPageGenericType;

    public PageResponseType(Type expectedPageGenericType) {
        this.expectedPageGenericType = expectedPageGenericType;
    }

    @Override
    public boolean matches(Type responseType) {
        return false;
    }

    @Override
    public Page<T> convert(Object response) {
        return null;
    }
}
