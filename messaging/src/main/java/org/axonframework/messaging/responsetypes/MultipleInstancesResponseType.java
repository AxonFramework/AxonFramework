/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.responsetypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import static org.axonframework.common.ReflectionUtils.unwrapIfType;

/**
 * A {@link ResponseType} implementation that will match with query
 * handlers which return a multiple instances of the expected response type. If matching succeeds, the
 * {@link ResponseType#convert(Object)} function will be called, which will cast the query handler it's response to a
 * {@link java.util.List} with generic type {@code R}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Steven van Beelen
 * @since 3.2
 */
public class MultipleInstancesResponseType<R> extends AbstractResponseType<List<R>> {

    private static final Logger logger = LoggerFactory.getLogger(MultipleInstancesResponseType.class);

    /**
     * Instantiate a {@link MultipleInstancesResponseType} with the given
     * {@code expectedCollectionGenericType} as the type to be matched against and which the convert function will use
     * as the generic for the {@link java.util.List} return value.
     *
     * @param expectedCollectionGenericType the response type which is expected to be matched against and returned
     */
    @JsonCreator
    @ConstructorProperties({"expectedResponseType"})
    public MultipleInstancesResponseType(@JsonProperty("expectedResponseType") Class<R> expectedCollectionGenericType) {
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
        Type unwrapped = unwrapIfType(responseType, Future.class);
        return isIterableOfExpectedType(unwrapped) ||
                isStreamOfExpectedType(unwrapped) ||
                isGenericArrayOfExpectedType(unwrapped) ||
                isArrayOfExpectedType(unwrapped);
    }

    /**
     * Converts the given {@code response} of type {@link java.lang.Object} into the type {@link java.util.List} with
     * generic type {@code R} from this {@link ResponseType} instance.
     * Will ensure that if the given {@code response} is of another collections format (e.g. an array, or a
     * {@link java.util.stream.Stream}) that it will be converted to a List.
     * Should only be called if {@link ResponseType#matches(Type)} returns true. Will throw an
     * {@link java.lang.IllegalArgumentException} if the given response is not convertible to a List of the expected
     * response type.
     *
     * @param response the {@link java.lang.Object} to convert into a {@link java.util.List} of generic type {@code R}
     * @return a {@link java.util.List} of generic type {@code R}, based on the given {@code response}
     */
    @SuppressWarnings("unchecked") // Suppress cast to array R, since in proper use of this function it is allowed
    @Override
    public List<R> convert(Object response) {
        Class<?> responseType = response.getClass();

        if (isArrayOfExpectedType(responseType)) {
            return Arrays.asList((R[]) response);
        } else if (isIterableOfExpectedType(response)) {
            return convertToList((Iterable) response);
        }

        throw new IllegalArgumentException("Retrieved response [" + responseType + "] is not convertible to a List of "
                                                   + "the expected response type [" + expectedResponseType + "]");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class responseMessagePayloadType() {
        return List.class;
    }

    private boolean isIterableOfExpectedType(Object response) {
        Class<?> responseType = response.getClass();

        boolean isIterableType = Iterable.class.isAssignableFrom(responseType);
        if (!isIterableType) {
            return false;
        }
        Iterator responseIterator = ((Iterable) response).iterator();

        boolean canMatchContainedType = responseIterator.hasNext();
        if (!canMatchContainedType) {
            logger.debug("The given response is an Iterable without any contents, hence we cannot verify if the "
                                + "contained type is assignable to the expected type.");
            return true;
        }

        return isAssignableFrom(responseIterator.next().getClass());
    }

    @SuppressWarnings("unchecked") // Suppress cast to R, since in proper use of this function it is allowed
    private List<R> convertToList(Iterable responseIterable) {
        List<R> response = new ArrayList<>();
        Iterator responseIterator = responseIterable.iterator();
        responseIterator.forEachRemaining(responseInstance -> response.add((R) responseInstance));
        return response;
    }

    @Override
    public String toString() {
        return "MultipleInstancesResponseType{" + expectedResponseType + "}";
    }
}
