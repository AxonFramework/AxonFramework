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

import java.util.stream.Stream;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/**
 * @author Rene de Waele
 */
public class AbstractSingleEntryUpcasterTest {

    @Test
    public void testUpcastingToNullResultsInEmptyStream() {
        Upcaster<Object> toNullUpcaster = new ToNullUpcaster();
        Stream<?> result = toNullUpcaster.upcast(new Object());
        assertFalse(result.findAny().isPresent());
    }

    @Test
    public void testRemainderMethodReturnsEmptyStreamByDefault() {
        Upcaster<Object> toNullUpcaster = new ObjectUpcaster();
        Stream<?> result = toNullUpcaster.upcast("some object");
        assertTrue(result.findAny().isPresent());
        Stream<?> remainder = toNullUpcaster.remainder();
        assertFalse(remainder.findAny().isPresent());
    }

    private static class ToNullUpcaster extends AbstractSingleEntryUpcaster<Object> {
        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            return null;
        }
    }

    private static class ObjectUpcaster extends AbstractSingleEntryUpcaster<Object> {
        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            return new Object();
        }
    }

}
