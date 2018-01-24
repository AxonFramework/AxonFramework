package org.axonframework.queryhandling;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @param <T>
 * @author Steven van Beelen
 * @since 3.2
 */
public class ListResponseType<T> implements ResponseType<List<T>> {

    private final Type expectedListGenericType;

    public ListResponseType(Type expectedListGenericType) {
        this.expectedListGenericType = expectedListGenericType;
    }

    @Override
    public boolean matches(Type responseType) {
        return false;
    }

    @Override
    public List<T> convert(Object response) {
        return null;
    }
}
