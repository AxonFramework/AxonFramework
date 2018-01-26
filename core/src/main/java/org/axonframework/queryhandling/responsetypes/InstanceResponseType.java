package org.axonframework.queryhandling.responsetypes;

import java.lang.reflect.Type;

/**
 * @param <R>
 * @author Steven van Beelen
 * @since 3.2
 */
public class InstanceResponseType<R> extends AbstractResponseType<R> {

    public InstanceResponseType(Class<?> expectedResponseType) {
        super(expectedResponseType);
    }

    @Override
    public boolean matches(Type responseType) {
        return isGenericAssignableFrom(responseType) || isAssignableFrom(responseType);
    }
}
