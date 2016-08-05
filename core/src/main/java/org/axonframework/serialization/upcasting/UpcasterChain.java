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
 * Represents a series of upcasters which are combined to upcast a stream of input representations to the most recent
 * revision of that representation.
 *
 * @author Rene de Waele
 */
public interface UpcasterChain<T> {

    /**
     * Pass the given Stream of {@code intermediateRepresentations} through the chain of upcasters. The result is a
     * list of zero or more representations representing the latest revision of the input objects.
     *
     * @param intermediateRepresentations  the representations to upcast
     * @return the Stream of upcast objects
     */
    Stream<T> upcast(Stream<T> intermediateRepresentations);

}
