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
        return isCollectionOfExpectedType(responseType) ||
                isArrayOfExpectedType(responseType) ||
                isGenericArrayOfExpectedType(responseType) ||
                isGenericAssignableFrom(responseType) ||
                isAssignableFrom(responseType);
    }

    @Override
    public R convert(Object response) {
        return null;
    }
}
