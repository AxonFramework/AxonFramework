/*
 * Copyright (c) 2010-2023. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.common.ReflectionUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.Future;

/**
 * A {@link ResponseType} implementation that will match with query handlers which return a single instance of the
 * expected response type, but returns that as an Optional containing the result. If matching succeeds, the {@link
 * ResponseType#convert(Object)} function will be called, which will wrap the query handler's response into an
 * Optional.
 * <p>
 * Note that this {@code ResponseType} will declare the same expectations on the Query Result as the {@code
 * ResponseType} returned by {@link InstanceResponseType}. The difference is that the result provided by this {@code
 * ResponseType} is wrapped in an {@link Optional}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Allard Buijze
 * @since 4.2
 */
public class OptionalResponseType<R> extends AbstractResponseType<Optional<R>> {

    /**
     * Instantiate a {@link OptionalResponseType} with the given {@code expectedResponseType} as the type to be matched
     * against and to which the query response should be converted to.
     *
     * @param expectedResponseType the response type which is expected to be matched against and returned
     */
    @JsonCreator
    @ConstructorProperties({"expectedResponseType"})
    public OptionalResponseType(@JsonProperty("expectedResponseType") Class<R> expectedResponseType) {
        super(expectedResponseType);
    }

    /**
     * Match the query handler its response {@link Type} with this implementation its responseType {@code R}. Will
     * return true if the expected type is assignable to the response type, taking generic types into account.
     *
     * @param responseType the response {@link Type} of the query handler which is matched against
     * @return true if the response type is assignable to the expected type, taking generic types into account
     */
    @Override
    public boolean matches(Type responseType) {
        Type unwrapped = ReflectionUtils.unwrapIfType(responseType, Future.class, Optional.class);
        return isGenericAssignableFrom(unwrapped) || isAssignableFrom(unwrapped);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<R> convert(Object response) {
        if (response instanceof Optional) {
            return (Optional<R>) response;
        } else if (response != null && projectReactorOnClassPath()) {
            if (Mono.class.isAssignableFrom(response.getClass())) {
                return Optional.ofNullable((R) ((Mono) response).block());
            } else if (Publisher.class.isAssignableFrom(response.getClass())) {
                return Optional.ofNullable((R) Mono.from((Publisher) response).block());
            }
        }
        return Optional.ofNullable((R) expectedResponseType.cast(response));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class responseMessagePayloadType() {
        return Optional.class;
    }

    @Override
    public ResponseType<?> forSerialization() {
        return ResponseTypes.instanceOf(expectedResponseType);
    }

    @Override
    public String toString() {
        return "OptionalResponseType{" + expectedResponseType + "}";
    }
}
