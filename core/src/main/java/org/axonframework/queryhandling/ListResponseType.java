package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 *
 * @param <T>
 * @author Steven van Beelen
 * @since 3.2
 */
public class ListResponseType<T> implements ResponseType<T> {

    private final Class<?> listType;

    public ListResponseType(Class<?> listType) {
        this.listType = listType;
    }

    @Override
    public boolean matches(Type type) {
        return false;
    }

    @Override
    public T convert(Object response) {
        return null;
    }
}
