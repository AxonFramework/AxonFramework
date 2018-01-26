package org.axonframework.queryhandling;

import com.coekie.gentyref.GenericTypeReflector;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * @param <R>
 * @author Steven van Beelen
 * @since 3.2
 */
public class InstanceResponseType<R> implements ResponseType<R> {

    private final Class<?> expectedResponseType;

    public InstanceResponseType(Class<?> expectedResponseType) {
        this.expectedResponseType = expectedResponseType;
    }

    @Override
    public boolean matches(Type responseType) {
        return isCollectionOfExpectedType(responseType) ||
                isArrayOfExpectedType(responseType) ||
                isGenericArrayOfExpectedType(responseType) ||
                isGenericAssignableFrom(responseType) ||
                isAssignableFrom(responseType);
    }

    private boolean isCollectionOfExpectedType(Type responseType) {
        boolean isParameterizedType = isParameterizedType(responseType);
        if (!isParameterizedType) {
            return false;
        }

        Type[] actualTypeArguments = ((ParameterizedType) responseType).getActualTypeArguments();
        return hasOneTypeArgumentWhichMatches(actualTypeArguments);
    }

    private boolean isParameterizedType(Type responseType) {
        return responseType instanceof ParameterizedType;
    }

    private boolean hasOneTypeArgumentWhichMatches(Type[] typeArguments) {
        boolean hasOneTypeArgument = typeArguments.length == 1;
        if (!hasOneTypeArgument) {
            return false;
        }

        Type responseType = typeArguments[0];
        return isAssignableFrom(responseType) ||
                isGenericAssignableFrom(responseType) ||
                isWildcardTypeWithMatchingUpperBound(responseType);
    }

    private boolean isWildcardTypeWithMatchingUpperBound(Type responseType) {
        boolean isWildcardType = isWildcardType(responseType);
        if (!isWildcardType) {
            return false;
        }

        Type[] upperBounds = ((WildcardType) responseType).getUpperBounds();
        return Arrays.stream(upperBounds).anyMatch(this::isAssignableFrom) ||
                Arrays.stream(upperBounds).anyMatch(this::isGenericAssignableFrom);
    }

    private boolean isWildcardType(Type responseType) {
        return responseType instanceof WildcardType;
    }

    private boolean isArrayOfExpectedType(Type responseType) {
        return responseType instanceof Class &&
                ((Class) responseType).isArray() &&
                isAssignableFrom(GenericTypeReflector.getArrayComponentType(responseType));
    }

    private boolean isGenericArrayOfExpectedType(Type responseType) {
        return responseType instanceof GenericArrayType &&
                isGenericAssignableFrom(((GenericArrayType) responseType).getGenericComponentType());
    }

    private boolean isGenericAssignableFrom(Type responseType) {
        return responseType instanceof TypeVariable &&
                Arrays.stream(((TypeVariable) responseType).getBounds())
                      .anyMatch(this::isAssignableFrom);
    }

    private boolean isAssignableFrom(Type responseType) {
        return responseType instanceof Class && expectedResponseType.isAssignableFrom((Class) responseType);
    }

    @Override
    public R convert(Object response) {
        return null;
    }
}
