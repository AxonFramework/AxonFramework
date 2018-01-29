package org.axonframework.queryhandling.responsetypes;

import com.coekie.gentyref.GenericTypeReflector;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * Abstract implementation of the {@link org.axonframework.queryhandling.responsetypes.ResponseType} which contains
 * match functions for the majority of the {@link java.lang.reflect.Type} options available.
 * For single instance response types, a direct assignable to check will be performed. For multiple instances response
 * types, the match will be performed against the containing type of that array/collection/etc.
 * Proves useful for reuse among ResponseType implementations.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Steven van Beelen
 * @since 3.2
 */
public abstract class AbstractResponseType<R> implements ResponseType<R> {

    protected final Class<?> expectedResponseType;

    /**
     * Instantiate a {@link org.axonframework.queryhandling.responsetypes.ResponseType} with the given
     * {@code expectedResponseType} as the type to be matched against and to which the query response should be
     * converted to, as is or as the contained type for an array/list/etc.
     *
     * @param expectedResponseType the response type which is expected to be matched against and to be returned, as is
     *                             or as the contained type for an array/list/etc
     */
    protected AbstractResponseType(Class<?> expectedResponseType) {
        this.expectedResponseType = expectedResponseType;
    }

    protected boolean isCollectionOfExpectedType(Type responseType) {
        boolean isParameterizedType = isParameterizedType(responseType);
        if (!isParameterizedType) {
            return false;
        }

        Type[] actualTypeArguments = ((ParameterizedType) responseType).getActualTypeArguments();
        return hasOneTypeArgumentWhichMatches(actualTypeArguments);
    }

    protected boolean isParameterizedType(Type responseType) {
        return responseType instanceof ParameterizedType;
    }

    protected boolean hasOneTypeArgumentWhichMatches(Type[] typeArguments) {
        boolean hasOneTypeArgument = typeArguments.length == 1;
        if (!hasOneTypeArgument) {
            return false;
        }

        Type responseType = typeArguments[0];
        return isAssignableFrom(responseType) ||
                isGenericAssignableFrom(responseType) ||
                isWildcardTypeWithMatchingUpperBound(responseType);
    }

    protected boolean isWildcardTypeWithMatchingUpperBound(Type responseType) {
        boolean isWildcardType = isWildcardType(responseType);
        if (!isWildcardType) {
            return false;
        }

        Type[] upperBounds = ((WildcardType) responseType).getUpperBounds();
        return Arrays.stream(upperBounds).anyMatch(this::isAssignableFrom) ||
                Arrays.stream(upperBounds).anyMatch(this::isGenericAssignableFrom);
    }

    protected boolean isWildcardType(Type responseType) {
        return responseType instanceof WildcardType;
    }

    protected boolean isArrayOfExpectedType(Type responseType) {
        return isArray(responseType) && isAssignableFrom(GenericTypeReflector.getArrayComponentType(responseType));
    }

    protected boolean isArray(Type responseType) {
        return responseType instanceof Class && ((Class) responseType).isArray();
    }

    protected boolean isGenericArrayOfExpectedType(Type responseType) {
        return responseType instanceof GenericArrayType &&
                isGenericAssignableFrom(((GenericArrayType) responseType).getGenericComponentType());
    }

    protected boolean isGenericAssignableFrom(Type responseType) {
        return responseType instanceof TypeVariable &&
                Arrays.stream(((TypeVariable) responseType).getBounds())
                      .anyMatch(this::isAssignableFrom);
    }

    protected boolean isAssignableFrom(Type responseType) {
        return responseType instanceof Class && expectedResponseType.isAssignableFrom((Class) responseType);
    }
}
