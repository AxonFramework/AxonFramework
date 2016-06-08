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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class GenericUpcasterChainTest {

    @Test
    public void testChainWithSingleUpcasterCanUpcastMultipleEvents() {
        Object a = "a", b = "b", c = "c";
        UpcasterChain<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(a, b));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(b, b, b, c), result.collect(toList()));
    }

    @Test
    public void testUpcastingResultOfOtherUpcaster() {
        Object a = "a", b = "b", c = "c";
        UpcasterChain<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(a, b), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(c, c, c, c), result.collect(toList()));
    }

    @Test
    public void testUpcastingResultOfOtherUpcasterOnlyWorksIfUpcastersAreInCorrectOrder() {
        Object a = "a", b = "b", c = "c";
        UpcasterChain<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(b, c), new AToBUpcaster(a, b));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(b, c, b, c), result.collect(toList()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNewUpcasterIsSuppliedForEachRoundOfUpcasting() {
        Object a = "a", b = "b", c = "c";
        Supplier<Upcaster<Object>> mockSupplier = mock(Supplier.class);
        when(mockSupplier.get()).thenReturn(new AToBUpcaster(a, b));
        UpcasterChain<Object> testSubject = new GenericUpcasterChain<>(Collections.singletonList(mockSupplier));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(b, b, b, c), result.collect(toList()));
        verify(mockSupplier).get();
        result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(b, b, b, c), result.collect(toList()));
        verify(mockSupplier, times(2)).get();
    }

    @Test
    public void testRemainderAddedAndUpcasted() {
        Object a = "a", b = "b", c = "c";
        UpcasterChain<Object> testSubject =
                new GenericUpcasterChain<>(new UpcasterWithRemainder(a, b), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertEquals(Arrays.asList(a, c, a, c, a, c), result.collect(toList()));
    }

    @Test
    public void testRemainderReleasedAfterUpcasting() {
        Object a = "a", b = "b", c = "c", d = "d";
        UpcasterChain<Object> testSubject =
                new GenericUpcasterChain<>(new ConditionalUpcaster(a, b, c), new ConditionalUpcaster(c, d, a));
        Stream<Object> result = testSubject.upcast(Stream.of(a, d, a, b, d, a));
        assertEquals(Arrays.asList(a, d, a, a), result.collect(toList()));
    }

    @Test
    public void testUpcastToMultipleTypes() {
        Object a = "a", b = "b", c = "c";
        UpcasterChain<Object> testSubject =
                new GenericUpcasterChain<>(new MultipleTypesUpcaster(), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, c));
        assertEquals(Arrays.asList(a, a, c, c, c, c), result.collect(toList()));
    }

    private static class AToBUpcaster extends AbstractSingleEntryUpcaster<Object> {
        private final Object a, b;

        private AToBUpcaster(Object a, Object b) {
            this.a = a;
            this.b = b;
        }

        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            return intermediateRepresentation == a ? b : intermediateRepresentation;
        }
    }

    private static class UpcasterWithRemainder extends AbstractSingleEntryUpcaster<Object> {

        private final List<Object> remainder;

        public UpcasterWithRemainder(Object... remainder) {
            this.remainder = Arrays.asList(remainder);
        }

        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            return intermediateRepresentation;
        }

        @Override
        public Stream<Object> remainder() {
            return remainder.stream();
        }
    }

    private static class ConditionalUpcaster extends AbstractSingleEntryUpcaster<Object> {

        private final Object first, second, replacement;
        private Object previous;

        public ConditionalUpcaster(Object first, Object second, Object replacement) {
            this.first = first;
            this.second = second;
            this.replacement = replacement;
        }

        @Override
        public Stream<Object> upcast(Object intermediateRepresentation) {
            if (previous == null) {
                if (intermediateRepresentation == first) {
                    previous = intermediateRepresentation;
                    return null;
                }
                return Stream.of(intermediateRepresentation);
            } else {
                if (intermediateRepresentation == second) {
                    previous = null;
                    return Stream.of(replacement);
                }
                Stream<Object> result = Stream.of(previous, intermediateRepresentation);
                previous = null;
                return result;
            }
        }

        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            if (previous == null) {
                if (intermediateRepresentation == first) {
                    previous = intermediateRepresentation;
                    return null;
                }
                return intermediateRepresentation;
            } else {
                if (intermediateRepresentation == second) {
                    previous = null;
                    return replacement;
                }

                return intermediateRepresentation;
            }
        }

        @Override
        public Stream<Object> remainder() {
            try {
                return previous == null ? Stream.empty() : Stream.of(previous);
            } finally {
                previous = null;
            }
        }
    }

    private static class MultipleTypesUpcaster implements Upcaster<Object> {

        @Override
        public Stream<Object> upcast(Object intermediateRepresentation) {
            return Stream.of(intermediateRepresentation, intermediateRepresentation);
        }

        @Override
        public Stream<Object> remainder() {
            return Stream.empty();
        }
    }

}