package org.axonframework.queryhandling.responsetypes;

import java.lang.reflect.Type;

/**
 * @param <R>
 * @author Steven van Beelen
 * @since 3.2
 */
public class PageResponseType<R> extends AbstractResponseType<Page<R>> {

    public PageResponseType(Class<?> expectedPageGenericType) {
        super(expectedPageGenericType);
    }

    @Override
    public boolean matches(Type responseType) {
        return false;
    }

    @Override
    public Page<R> convert(Object response) {
        return null;
    }
}
