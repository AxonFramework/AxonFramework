/*
 * Copyright (c) 2010-2018. Axon Framework
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

import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * Specifies the expected response type required when performing a query through the
 * {@link org.axonframework.queryhandling.QueryBus}/{@link org.axonframework.queryhandling.QueryGateway}.
 * By wrapping the response type as a generic {@code R}, we can easily service the expected response as a single
 * instance, a list, a page etc., based on the selected implementation even while the query handler return type might be
 * slightly different.
 * </p>
 * It is in charge of matching the response type of a query handler with the given generic {@code R}.
 * If this match returns true, it signals the found query handler can handle the intended query.
 * As a follow up, the response retrieved from a query handler should move through the
 * {@link ResponseType#convert(Object)} function to guarantee the right response type is returned.
 *
 * @param <R> the generic type of this {@link ResponseType} to be matched and converted.
 * @author Steven van Beelen
 * @see ResponseTypes to instantiate a {@link ResponseType} implementation
 * @since 3.2
 */
public interface ResponseType<R> extends Serializable {

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with the {@link ResponseType} implementation
     * its expected response type {@code R}. Will return true if a response can be converted based on the given
     * {@code responseType} and false if it cannot.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if a response can be converted based on the given {@code responseType} and false if it cannot
     */
    boolean matches(Type responseType);

    /**
     * Converts the given {@code response} of type {@link java.lang.Object} into the type {@code R} of this
     * {@link ResponseType} instance. Should only be called if {@link ResponseType#matches(Type)} returns true.
     * It is unspecified what this function does if the {@link ResponseType#matches(Type)} returned false.
     *
     * @param response the {@link java.lang.Object} to convert into {@code R}
     * @return a {@code response} of type {@code R}
     */
    @SuppressWarnings("unchecked")
    default R convert(Object response) {
        return (R) response;
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
}
