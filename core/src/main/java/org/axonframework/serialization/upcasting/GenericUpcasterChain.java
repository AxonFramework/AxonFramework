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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Rene de Waele
 */
public class GenericUpcasterChain<T> implements UpcasterChain<T> {

    private final List<Supplier<Upcaster<T>>> upcasterSuppliers;

    @SafeVarargs
    public GenericUpcasterChain(Upcaster<T>... upcasters) {
        this(Arrays.stream(upcasters).map(upcaster -> (Supplier<Upcaster<T>>) () -> upcaster).collect(toList()));
    }

    public GenericUpcasterChain(List<Supplier<Upcaster<T>>> upcasterSuppliers) {
        this.upcasterSuppliers = new ArrayList<>(upcasterSuppliers);
    }

    @Override
    public Stream<T> upcast(Stream<T> intermediateRepresentations) {
        List<Upcaster<T>> upcasters = getUpcasters();
        Stream<T> result = intermediateRepresentations.flatMap(intermediateRepresentation -> {
            Stream<T> entryResult = Stream.of(intermediateRepresentation);
            for (Upcaster<T> upcaster : upcasters) {
                entryResult = entryResult.flatMap(upcaster::upcast);
            }
            return entryResult;
        });
        Stream<T> remainder = upcasters.stream().flatMap(upcaster -> {
            Stream<T> entryResult = upcaster.remainder();
            for (Upcaster<T> otherUpcaster : upcasters.subList(upcasters.indexOf(upcaster) + 1, upcasters.size())) {
                entryResult = entryResult.flatMap(otherUpcaster::upcast);
            }
            return entryResult;
        });
        return Stream.concat(result, remainder);
    }

    protected List<Upcaster<T>> getUpcasters() {
        return upcasterSuppliers.stream().map(Supplier::get).collect(toList());
    }

}
