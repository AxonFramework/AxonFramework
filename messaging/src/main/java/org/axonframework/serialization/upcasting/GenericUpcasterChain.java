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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of an {@link Upcaster} that is formed of a chain of other upcasters which are combined to upcast a
 * stream of intermediate objects.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class GenericUpcasterChain<T> implements Upcaster<T> {

    private final List<? extends Upcaster<T>> upcasters;

    /**
     * Initializes an upcaster chain from one or more upcasters.
     *
     * @param upcasters the upcasters to chain
     */
    @SafeVarargs
    public GenericUpcasterChain(Upcaster<T>... upcasters) {
        this(Arrays.asList(upcasters));
    }

    /**
     * Initializes an upcaster chain from the given list of upcasters.
     *
     * @param upcasters the upcasters to chain
     */
    public GenericUpcasterChain(List<? extends Upcaster<T>> upcasters) {
        this.upcasters = new ArrayList<>(upcasters);
    }

    @Override
    public Stream<T> upcast(Stream<T> initialRepresentations) {
        Stream<T> result = initialRepresentations;
        for (Upcaster<T> upcaster : getUpcasters()) {
            result = upcaster.upcast(result);
        }
        return result;
    }

    /**
     * Returns the list of {@link Upcaster upcasters} that makes up this chain.
     *
     * @return the upcaster chain
     */
    protected List<? extends Upcaster<T>> getUpcasters() {
        return upcasters;
    }
}
