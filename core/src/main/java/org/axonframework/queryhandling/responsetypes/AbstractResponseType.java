package org.axonframework.queryhandling.responsetypes;

import com.coekie.gentyref.GenericTypeReflector;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 *
 * @param <R>
 */
public abstract class AbstractResponseType<R> implements ResponseType<R> {

    protected final Class<?> expectedResponseType;

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
        return responseType instanceof Class &&
                ((Class) responseType).isArray() &&
                isAssignableFrom(GenericTypeReflector.getArrayComponentType(responseType));
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
