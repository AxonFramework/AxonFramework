package org.axonframework.queryhandling.responsetypes;

import java.lang.reflect.Type;
import java.util.List;

/**
 * A {@link org.axonframework.queryhandling.responsetypes.ResponseType} implementation that will match with query
 * handlers which return a multiple instances of the expected response type. If matching succeeds, the
 * {@link ResponseType#convert(Object)} function will be called, which will cast the query handler it's response to a
 * {@link java.util.List} with generic type {@code R}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Steven van Beelen
 * @since 3.2
 */
public class MultipleInstancesResponseType<R> extends AbstractResponseType<List<R>> {

    /**
     * Instantiate a {@link org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType} with the given
     * {@code expectedCollectionGenericType} as the type to be matched against and which the convert function will use
     * as the generic for the {@link java.util.List} return value.
     *
     * @param expectedCollectionGenericType the response type which is expected to be matched against and returned
     */
    public MultipleInstancesResponseType(Class<R> expectedCollectionGenericType) {
        super(expectedCollectionGenericType);
    }

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with this implementation its responseType
     * {@code R}.
     * Will return true in the following scenarios:
     * <ul>
     * <li>If the response type is an array of the expected type. For example a {@code ExpectedType[]}</li>
     * <li>If the response type is a {@link java.lang.reflect.GenericArrayType} of the expected type.
     * For example a {@code <E extends ExpectedType> E[]}</li>
     * <li>If the response type is a {@link java.lang.reflect.ParameterizedType} containing a single
     * {@link java.lang.reflect.TypeVariable} which is assignable to the response type, taking generic types into
     * account. For example a {@code List<ExpectedType>} or {@code <E extends ExpectedType> List<E>}.</li>
     * <li>If the response type is a {@link java.lang.reflect.ParameterizedType} containing a single
     * {@link java.lang.reflect.WildcardType} which is assignable to the response type, taking generic types into
     * account. For example a {@code <E extends ExpectedType> List<? extends E>}.</li>
     * </ul>
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true for arrays, generic arrays and {@link java.lang.reflect.ParameterizedType}s (like a
     * {@link java.lang.Iterable}) for which the contained type is assignable to the expected type
     */
    @Override
    public boolean matches(Type responseType) {
        return isCollectionOfExpectedType(responseType) ||
                isGenericArrayOfExpectedType(responseType) ||
                isArrayOfExpectedType(responseType);
    }

    /**
     * Converts the given {@code response} of type {@link java.lang.Object} into the type {@link java.util.List} with
     * generic type {@code R} from this {@link org.axonframework.queryhandling.responsetypes.ResponseType} instance.
     * Will ensure that if the given {@code response} is of another collections format (e.g. an array, or a
     * {@link java.util.stream.Stream}) that it will be converted to a List.
     * Should only be called if {@link ResponseType#matches(Type)} returns true.
     *
     * @param response the {@link java.lang.Object} to convert into a {@link java.util.List} of generic type {@code R}
     * @return a {@link java.util.List} of generic type {@code R}, based on the given {@code response}
     */
    @Override
    public List<R> convert(Object response) {
        return null;
    }
}
