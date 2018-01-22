package org.axonframework.queryhandling;

import java.lang.reflect.Type;

/**
 *
 * @param <T>
 * @author Steven van Beelen
 * @since 3.2
 */
public class PageResponseType<T> implements ResponseType<Page<T>> {

    private final Class<?> pageType;

    public PageResponseType(Class<?> pageType) {
        this.pageType = pageType;
    }

    @Override
    public boolean matches(Type type) {
        return false;
    }

    @Override
    public Page<T> convert(Object response) {
        return null;
    }
}
