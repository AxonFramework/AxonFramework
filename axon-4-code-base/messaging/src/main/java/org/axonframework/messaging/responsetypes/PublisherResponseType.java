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

import java.util.concurrent.CompletableFuture;
import reactor.core.CompletableFuture.Flux;
import reactor.core.CompletableFuture.Mono;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * A {@link ResponseType} implementation that will match with query
 * handlers that return a {@link CompletableFuture} stream of the expected response type. If matching succeeds, the
 * {@link ResponseType#convert(Object)} function will be called, which will cast the query handler it's response to
 * {@code R}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Stefan Dragisic
 * @author Milan Savic
 * @since 4.6.0
 */
public class CompletableFutureResponseType<R> extends AbstractResponseType<CompletableFuture<R>> {

    /**
     * Indicates that the response matches with the {@link java.lang.reflect.Type} while returning a CompletableFuture result.
     *
     * @see ResponseType#MATCH
     * @see ResponseType#NO_MATCH
     */
    public static final int CompletableFuture_MATCH = 2048;
    private final ResponseType<?> multipleInstanceResponseType;

    /**
     * Instantiate a {@link CompletableFutureResponseType} with the given
     * {@code expectedResponseType} as the type to be matched against and to which the query response should be
     * converted to.
     *
     * @param expectedResponseType the response type which is expected to be matched against and returned
     */
    public CompletableFutureResponseType(Class<?> expectedResponseType) {
        super(expectedResponseType);
        multipleInstanceResponseType = new MultipleInstancesResponseType<>(expectedResponseType);
    }

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with this implementation its responseType
     * {@code R}.
     * Will return true if the expected type is assignable to the response type, taking generic types into account.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if the response type is assignable to the expected type, taking generic types into account
     */
    @Override
    public boolean matches(Type responseType) {
        return matchRank(responseType) > ResponseType.NO_MATCH;
    }

    @Override
    public Integer matchRank(Type responseType) {
        if (isCompletableFutureOfExpectedType(responseType)) {
            return CompletableFuture_MATCH;
        }
        return multipleInstanceResponseType.matchRank(responseType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class responseMessagePayloadType() {
        return Flux.class;
    }

    @Override
    public CompletableFuture<R> convert(Object queryResponse) {
        if (!projectReactorOnClassPath()) {
            return null;
        }
        if (queryResponse == null) {
            return Flux.empty();
        } else if (CompletableFuture.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.from((CompletableFuture) queryResponse);
        } else if (Iterable.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.fromIterable((Iterable) queryResponse);
        } else if (Stream.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.fromStream((Stream) queryResponse);
        } else if (CompletableFuture.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.from(Mono.fromCompletionStage((CompletableFuture) queryResponse));
        } else if (Optional.class.isAssignableFrom(queryResponse.getClass())) {
            return (Flux<R>) ((Optional) queryResponse).map(Flux::just).orElse(Flux.<R>empty());
        }
        return Flux.just((R) queryResponse);
    }

    @Override
    public Optional<CompletableFuture<R>> convertExceptional(Throwable e) {
        return Optional.of(Flux.error(e));
    }

    @Override
    public ResponseType<?> forSerialization() {
        return ResponseTypes.multipleInstancesOf(expectedResponseType);
    }
}
