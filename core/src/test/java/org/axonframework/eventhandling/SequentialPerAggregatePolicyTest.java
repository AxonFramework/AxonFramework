/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.MetaData;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SequentialPerAggregatePolicyTest {

    @Test
    public void testSequentialIdentifier() {
        // ok, pretty useless, but everything should be tested
        SequentialPerAggregatePolicy testSubject = new SequentialPerAggregatePolicy();
        Object aggregateIdentifier = UUID.randomUUID();
        Object id1 = testSubject.getSequenceIdentifierFor(newStubDomainEvent(aggregateIdentifier));
        Object id2 = testSubject.getSequenceIdentifierFor(newStubDomainEvent(aggregateIdentifier));
        Object id3 = testSubject.getSequenceIdentifierFor(newStubDomainEvent(UUID.randomUUID()));
        Object id4 = testSubject.getSequenceIdentifierFor(new GenericEventMessage<String>("bla"));

        assertEquals(id1, id2);
        assertFalse(id1.equals(id3));
        assertFalse(id2.equals(id3));
        assertNull(id4);
    }

    private DomainEventMessage newStubDomainEvent(Object aggregateIdentifier) {
        return new GenericDomainEventMessage<Object>(aggregateIdentifier, (long) 0,
                                                     new Object(), MetaData.emptyInstance());
    }
}
