package org.axonframework.queryhandling.responsetypes;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @param <R>
 * @author Steven van Beelen
 * @since 3.2
 */
public class ListResponseType<R> extends AbstractResponseType<List<R>> {

    public ListResponseType(Class<?> expectedListGenericType) {
        super(expectedListGenericType);
    }

    @Override
    public boolean matches(Type responseType) {
        return false;
    }

    @Override
    public List<R> convert(Object response) {
        return null;
    }
}
