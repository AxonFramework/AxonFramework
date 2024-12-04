/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests resolving query handlers which return primitive types.
 */
class PrimitiveQueryHandlerResponseTypeTest {

    private final SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
    private final PrimitiveQueryHandler queryHandler = new PrimitiveQueryHandler();
    private final AnnotationQueryHandlerAdapter<PrimitiveQueryHandler> annotationQueryHandlerAdapter =
            new AnnotationQueryHandlerAdapter<>(queryHandler);

    @BeforeEach
    void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    void testInt() {
        test(0, Integer.class, int.class);
    }

    @Test
    void testLong() {
        test(0L, Long.class, long.class);
    }

    @Test
    void testShort() {
        test((short) 0, Short.class, short.class);
    }

    @Test
    void testFloat() {
        test(0.0F, Float.class, float.class);
    }

    @Test
    void testDouble() {
        test(0.0, Double.class, double.class);
    }

    @Test
    void testBoolean() {
        test(false, Boolean.class, boolean.class);
    }

    @Test
    void testByte() {
        test((byte) 0, Byte.class, byte.class);
    }

    @Test
    void testChar() {
        test('0', Character.class, Character.TYPE);
    }

    /**
     * Sends out two queries to ensure they both can be resolved - the first expecting a response type of the wrapper
     * class, and the second expecting a response type of the primitive class.
     *
     * @param value     a {@link T} value used as the query
     * @param boxed     the boxed primitive wrapper type, eg {@link Integer}.class, {@link Long}.class, etc.
     * @param primitive the unboxed primitive type, eg {@code int}.class, {@code long}.class, etc.
     * @param <T>       the type being tested
     */
    private <T> void test(final T value, final Class<T> boxed, final Class<T> primitive) {
        final QueryMessage<T, T> queryBoxed = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), value, ResponseTypes.instanceOf(boxed)
        );
        final QueryMessage<T, T> queryPrimitive = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), value, ResponseTypes.instanceOf(primitive)
        );

        final T responseBoxed = queryBus.query(queryBoxed).join().getPayload();
        final T responsePrimitive = queryBus.query(queryPrimitive).join().getPayload();

        assertEquals(value, responseBoxed);
        assertEquals(value, responsePrimitive);
    }

    @SuppressWarnings("unused")
    private static class PrimitiveQueryHandler {

        @QueryHandler
        public int handle(final Integer query) {
            return query;
        }

        @QueryHandler
        public long handle(final Long query) {
            return query;
        }

        @QueryHandler
        public short handle(final Short query) {
            return query;
        }

        @QueryHandler
        public float handle(final Float query) {
            return query;
        }

        @QueryHandler
        public double handle(final Double query) {
            return query;
        }

        @QueryHandler
        public boolean handle(final Boolean query) {
            return query;
        }

        @QueryHandler
        public byte handle(final Byte query) {
            return query;
        }

        @QueryHandler
        public char handle(final Character query) {
            return query;
        }
    }
}
