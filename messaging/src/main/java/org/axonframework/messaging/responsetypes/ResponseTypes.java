/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Optional;

/**
 * Utility class containing static methods to obtain instances of {@link ResponseType}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public abstract class ResponseTypes {

    /**
     * Specify the desire to retrieve a single instance of type {@code R} when performing a query.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated {@link ResponseType}
     * @return a {@link ResponseType} specifying the desire to retrieve a single instance of type {@code R}
     */
    public static <R> ResponseType<R> instanceOf(Class<R> type) {
        return new InstanceResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve an {@code Optional<R>} when performing a query.
     * <p>
     * Note that this {@code ResponseType} will declare the same expectations on the Query Result as the {@code
     * ResponseType} returned by {@link #instanceOf(Class)}. The difference is that the result provided by this {@code
     * ResponseType} is wrapped in an {@code Optional}.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated {@link ResponseType}
     * @return a {@link ResponseType} specifying the desire to retrieve an optional instance of type {@code R}
     */
    public static <R> ResponseType<Optional<R>> optionalInstanceOf(Class<R> type) {
        return new OptionalResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve a collection of instances of type {@code R} when performing a query.
     * If no handler is found that can retrieve the type as a collection, a handler is selected that returns a single
     * instance of the provided type.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated {@link ResponseType}
     * @return a {@link ResponseType} specifying the desire to retrieve a collection of instances of type {@code R}
     */
    public static <R> ResponseType<List<R>> multipleInstancesOf(Class<R> type) {
        return new MultipleInstancesResponseType<>(type);
    }

    /**
     * Specify the desire to retrieve a Publisher (reactive stream) of instances of type {@code R} when performing a
     * query.
     *
     * @param type the {@code R} which is expected to be the response type
     * @param <R>  the generic type of the instantiated {@link ResponseType}
     * @return a {@link ResponseType} specifying the desire to retrieve a publisher of instances of type {@code R}
     */
    public static <R> ResponseType<Publisher<R>> publisherOf(Class<R> type) {
        return new PublisherResponseType<>(type);
    }

    private ResponseTypes() {
        // Utility class
    }
}
