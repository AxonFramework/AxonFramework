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
public class GenericUpcasterChain<T> implements Upcaster<T> {

    private final List<Supplier<Upcaster<T>>> upcasterSuppliers;

    @SafeVarargs
    public GenericUpcasterChain(Upcaster<T>... upcasters) {
        this(Arrays.stream(upcasters).map(upcaster -> (Supplier<Upcaster<T>>) () -> upcaster).collect(toList()));
    }

    public GenericUpcasterChain(List<Supplier<Upcaster<T>>> upcasterSuppliers) {
        this.upcasterSuppliers = new ArrayList<>(upcasterSuppliers);
    }

    @Override
    public Stream<T> upcast(Stream<T> initialRepresentations) {
        Stream<T> result = initialRepresentations;
        for (Upcaster<T> upcaster : getUpcasters()) {
            result = upcaster.upcast(result);
        }
        return result;
    }

    protected List<Upcaster<T>> getUpcasters() {
        return upcasterSuppliers.stream().map(Supplier::get).collect(toList());
    }

}
