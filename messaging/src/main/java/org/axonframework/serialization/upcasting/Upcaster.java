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

package org.axonframework.serialization.upcasting;

import java.util.stream.Stream;

/**
 * Interface for Upcasters. An upcaster is the mechanism used to convert deprecated (typically serialized) objects and
 * convert them into the current format. If the serializer itself is not able to cope with the changed formats (XStream,
 * for example, will allow for some changes by using aliases), the Upcaster will allow you to configure more complex
 * structural transformations.
 * <p/>
 * Upcasters work on intermediate representations of the object to upcast. In some cases, this representation contains a
 * byte array, while in other cases they contain an object structure. For performance reasons, it is advisable to ensure
 * that all upcasters in the same upcaster chain (where one's output is another's input) use the same intermediate
 * representation content type.
 *
 * @param <T> The data format that this upcaster uses to represent the data
 * @author Rene de Waele
 * @since 3.0
 */
@FunctionalInterface
public interface Upcaster<T> {

    /**
     * Apply this upcaster to a stream of {@code intermediateRepresentations} and return the altered stream.
     * <p>
     * To upcast an object an upcaster should apply a mapping function to the input stream ({@link
     * Stream#map(java.util.function.Function)}). To remove an object from the stream the upcaster should filter the
     * input stream ({@link Stream#filter(java.util.function.Predicate)}). To split an input object into more than one
     * objects use {@link Stream#flatMap(java.util.function.Function)}.
     * <p>
     * In some cases the upcasting result of an Upcaster may depend on more than one input object. In that case an
     * Upcaster may store state in the method during upcasting or even read ahead in the stream.
     *
     * @param intermediateRepresentations The input stream of representations of objects
     * @return the output stream of representations after applying the upcast function
     */
    Stream<T> upcast(Stream<T> intermediateRepresentations);
}
