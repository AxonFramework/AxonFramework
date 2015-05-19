/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.upcasting;

import org.axonframework.common.io.IOUtils;
import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.junit.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class SimpleUpcasterChainTest extends UpcasterChainTest {

    @Override
    protected UpcasterChain createUpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        return new SimpleUpcasterChain(converterFactory, upcasters);
    }

    @Test
    public void testEmptyUpcasterChain() {
        UpcasterChain chain = new SimpleUpcasterChain(Collections.<Upcaster>emptyList());
        final SimpleSerializedObject serializedObject = new SimpleSerializedObject<String>("Data", String.class,
                                                                                           "test", "0");
        List<SerializedObject> result = chain.upcast(serializedObject,
                                                     new SerializedDomainEventUpcastingContext(
                                                             new SimpleSerializedDomainEventData(
                                                             "eventId", "aggregateId", 0, ZonedDateTime.now(), "test", "0",
                                                             "Data".getBytes(IOUtils.UTF8),
                                                             "meta".getBytes(IOUtils.UTF8)
                                                             ), mock(Serializer.class))
        );

        assertEquals(Collections.<SerializedObject>singletonList(serializedObject), result);
        assertSame(serializedObject, result.get(0));
    }
}
