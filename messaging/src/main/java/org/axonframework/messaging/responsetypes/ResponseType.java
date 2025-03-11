/*
 * Copyright (c) 2010-2025. Axon Framework
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

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Specifies the expected response type required when performing a query through the {@link
 * org.axonframework.queryhandling.QueryBus}/{@link org.axonframework.queryhandling.QueryGateway}. By wrapping the
 * response type as a generic {@code R}, we can easily service the expected response as a single instance, a list, a
 * page etc., based on the selected implementation even while the query handler return type might be slightly
 * different.
 * </p>
 * It is in charge of matching the response type of a query handler with the given generic {@code R}. If this match
 * returns true, it signals the found query handler can handle the intended query. As a follow up, the response
 * retrieved from a query handler should move through the {@link ResponseType#convert(Object)} function to guarantee the
 * right response type is returned.
 *
 * @param <R> the generic type of this {@link ResponseType} to be matched and converted.
 * @author Steven van Beelen
 * @see ResponseTypes to instantiate a {@link ResponseType} implementation
 * @since 3.2
 */
public interface ResponseType<R> {

    /**
     * Indicates that the response type does not match with a given {@link java.lang.reflect.Type}.
     */
    int NO_MATCH = 0;

    /**
     * Indicates that the response matches with the {@link java.lang.reflect.Type} while returning a single result.
     */
    int MATCH = 1;

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with the {@link ResponseType} implementation
     * its expected response type {@code R}. Will return true if a response can be converted based on the given {@code
     * responseType} and false if it cannot.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if a response can be converted based on the given {@code responseType} and false if it cannot
     */
    boolean matches(Type responseType);

    /**
     * Defines the match and its priority. The greater the value above 0, the better the match. This is particularly
     * useful for {@link MultipleInstancesResponseType MultipleInstancesResponseTypes} when there are matches on a both
     * multiple and single instance types. Lists should be given priority for handling.
     * <p>
     * {@see ResponseType#ITERABLE_MATCH} {@see ResponseType#SINGLE_MATCH} {@see ResponseType#NO_MATCH}
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return {@link ResponseType#NO_MATCH} if there is no match, greater than 0 if there is a match. Highest match
     * should win when choosing a query handler.
     */
    default Integer matchRank(Type responseType) {
        if (matches(responseType)) {
            return MATCH;
        }
        return NO_MATCH;
    }

    /**
     * Converts the given {@code response} of type {@link java.lang.Object} into the type {@code R} of this {@link
     * ResponseType} instance. Should only be called if {@link ResponseType#matches(Type)} returns true. It is
     * unspecified what this function does if the {@link ResponseType#matches(Type)} returned false.
     *
     * @param response the {@link java.lang.Object} to convert into {@code R}
     * @return a {@code response} of type {@code R}
     */
    @SuppressWarnings("unchecked")
    default R convert(Object response) {
        return (R) response;
    }

    /**
     * Converts the given {@link java.lang.Throwable} into the type {@code R} of this {@link ResponseType} instance.
     * Used when an error is represented as the message payload. The {@link Optional} is not empty when an error is
     * represented as the message payload.
     *
     * @return a {@link Optional} {@code response} of type {@code R}
     */
    default Optional<R> convertExceptional(Throwable e) {
        return Optional.empty();
    }

    /**
     * Returns a {@link java.lang.Class} representing the type of the payload to be contained in the response message.
     *
     * @return a {@link java.lang.Class} representing the type of the payload to be contained in the response message
     */
    Class<R> responseMessagePayloadType();

    /**
     * Gets actual response type or generic placeholder.
     *
     * @return actual response type or generic placeholder
     */
    Class<?> getExpectedResponseType();

    /**
     * Returns the {@code ResponseType} instance that should be used when serializing responses. This method has a
     * default implementation that returns {@code this}. Implementations that describe a Response Type that is not
     * suited for serialization, should return an alternative that is suitable, and ensure the {@link #convert(Object)}
     * is capable of converting that type of response to the request type in this instance.
     *
     * @return a {@code ResponseType} instance describing a type suitable for serialization
     */
    default ResponseType<?> forSerialization() {
        return this;
    }
}
