/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * that all upcasters in the same {@link UpcasterChain} (where one's output is another's input) use the same
 * intermediate representation content type.
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Rene de Waele
 */
public interface Upcaster<T> {

    /**
     * Upcasts the given {@code intermediateRepresentation} to a Stream containing zero or more upcasted
     * representations.
     * <p>
     * If this upcaster cannot upcast the given input object or upcasting is not required it should simply return a
     * Stream containing only the given input object. The returned Stream may be left empty if the input object is to be
     * ignored. An Upcaster may also decide to 'split up' the given input into a Stream containing more than one output
     * objects.
     * <p>
     * In some cases the upcasting result of an Upcaster may depend on more than one input object. In that case an
     * Upcaster may build up state while it is used to upcast a stream of input objects.
     *
     * @param intermediateRepresentation The representation of the object to upcast
     * @return the new representations of the input object
     */
    Stream<T> upcast(T intermediateRepresentation);

    /**
     * Method that is invoked by the {@link UpcasterChain} after all input objects have been upcast. This method is
     * invoked to enable stateful Upcasters to release any remaining objects. Defaults to an empty stream.
     *
     * @return A Stream of remainder representations
     */
    default Stream<T> remainder() {
        return Stream.empty();
    }
}
