/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.responsetypes;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.TypeReflectionUtils;
import org.axonframework.util.ClasspathResolver;
import org.reactivestreams.Publisher;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Abstract implementation of the {@link ResponseType} which contains
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
     * Instantiate a {@link ResponseType} with the given
     * {@code expectedResponseType} as the type to be matched against and to which the query response should be
     * converted to, as is or as the contained type for an array/list/etc.
     *
     * @param expectedResponseType the response type which is expected to be matched against and to be returned, as is
     *                             or as the contained type for an array/list/etc
     */
    protected AbstractResponseType(Class<?> expectedResponseType) {
        this.expectedResponseType = (Class<?>) ReflectionUtils
                .resolvePrimitiveWrapperTypeIfPrimitive(expectedResponseType);
    }

    @Override
    public Class<?> getExpectedResponseType() {
        return expectedResponseType;
    }

    /**
     * Tries to unwrap generic type if provided {@code type} is of type {@link Future}.
     *
     * @param type to be unwrapped
     * @return unwrapped generic, or original if provided {@code type} is not of type {@link Future}
     * @deprecated Use {@link ReflectionUtils#unwrapIfType(Type, Class[])} instead
     */
    @Deprecated
    protected Type unwrapIfTypeFuture(Type type) {
        return ReflectionUtils.unwrapIfType(type, Future.class);
    }

    protected boolean isPublisherOfExpectedType(Type responseType) {
        Type publisherType = TypeReflectionUtils.getExactSuperType(responseType, Publisher.class);
        return publisherType != null && isParameterizedTypeOfExpectedType(publisherType);
    }

    protected boolean isIterableOfExpectedType(Type responseType) {
        Type iterableType = TypeReflectionUtils.getExactSuperType(responseType, Iterable.class);
        return iterableType != null && isParameterizedTypeOfExpectedType(iterableType);
    }

    protected boolean isStreamOfExpectedType(Type responseType) {
        Type streamType = TypeReflectionUtils.getExactSuperType(responseType, Stream.class);
        return streamType != null && isParameterizedTypeOfExpectedType(streamType);
    }

    protected boolean isParameterizedTypeOfExpectedType(Type responseType) {
        boolean isParameterizedType = isParameterizedType(responseType);
        if (!isParameterizedType) {
            return false;
        }

        Type[] actualTypeArguments = ((ParameterizedType) responseType).getActualTypeArguments();
        boolean hasOneTypeArgument = actualTypeArguments.length == 1;
        if (!hasOneTypeArgument) {
            return false;
        }

        Type actualTypeArgument = actualTypeArguments[0];
        return isAssignableFrom(actualTypeArgument) ||
                isGenericAssignableFrom(actualTypeArgument) ||
                isWildcardTypeWithMatchingUpperBound(actualTypeArgument);
    }

    protected boolean isParameterizedType(Type responseType) {
        return responseType instanceof ParameterizedType;
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
        return isArray(responseType) && isAssignableFrom(((Class) responseType).getComponentType());
    }

    protected boolean isArray(Type responseType) {
        return responseType instanceof Class && ((Class) responseType).isArray();
    }

    protected boolean isGenericArrayOfExpectedType(Type responseType) {
        return isGenericArrayType(responseType) &&
                isGenericAssignableFrom(((GenericArrayType) responseType).getGenericComponentType());
    }

    protected boolean isGenericArrayType(Type responseType) {
        return responseType instanceof GenericArrayType;
    }

    protected boolean isGenericAssignableFrom(Type responseType) {
        return isTypeVariable(responseType) &&
                Arrays.stream(((TypeVariable) responseType).getBounds())
                      .anyMatch(this::isAssignableFrom);
    }

    protected boolean isTypeVariable(Type responseType) {
        return responseType instanceof TypeVariable;
    }

    protected boolean isAssignableFrom(Type responseType) {
        return responseType instanceof Class && expectedResponseType.isAssignableFrom((Class) responseType);
    }

    protected boolean projectReactorOnClassPath() {
        return ClasspathResolver.projectReactorOnClasspath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractResponseType<?> that = (AbstractResponseType<?>) o;
        return Objects.equals(expectedResponseType, that.expectedResponseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedResponseType);
    }
}
