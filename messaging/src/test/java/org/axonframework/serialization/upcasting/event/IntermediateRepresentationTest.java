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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.utils.TestDomainEventEntry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for intermediate representation.
 *
 * @author Simon Zambrovski
 */
class IntermediateRepresentationTest {

    private final static Serializer serializer = TestSerializer.XSTREAM.getSerializer();

    @Test
    public void canConvertDataTo() {
        DomainEventMessage<String> testEvent = new GenericDomainEventMessage<>(
                "test", "aggregateId", 0, dottedName("test.event"), "someString"
        );
        EventData<?> eventData = new TestDomainEventEntry(testEvent, serializer);
        Serializer serializer = mock(Serializer.class);
        Converter converter = mock(Converter.class);
        when(serializer.getConverter()).thenReturn(converter);
        when(converter.canConvert(any(), eq(String.class))).thenReturn(true);

        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        EventUpcasterChain eventUpcasterChain = new EventUpcasterChain(
                new IntermediateRepresentationTest.MyEventUpcaster()
        );
        List<IntermediateEventRepresentation> result = eventUpcasterChain.upcast(Stream.of(input)).toList();
        assertEquals(1, result.size());


        assertTrue(input.canConvertDataTo(String.class));
        assertTrue(result.getFirst().canConvertDataTo(String.class));

        verify(converter).canConvert(String.class, String.class);
    }

    private static class MyEventUpcaster extends SingleEventUpcaster {

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return true;
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return new UpcastedEventRepresentation<>(
                    intermediateRepresentation.getType(),
                    intermediateRepresentation,
                    Function.identity(),
                    Function.identity(),
                    Object.class,
                    serializer.getConverter()
            );
        }
    }
}
