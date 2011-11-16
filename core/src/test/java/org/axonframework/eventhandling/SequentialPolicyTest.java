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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SequentialPolicyTest {

    @Test
    public void testSequencingIdentifier() {
        // ok, pretty useless, but everything should be tested
        SequentialPolicy testSubject = new SequentialPolicy();
        Object id1 = testSubject
                .getSequenceIdentifierFor(newStubDomainEvent(new UUIDAggregateIdentifier()));
        Object id2 = testSubject
                .getSequenceIdentifierFor(newStubDomainEvent(new UUIDAggregateIdentifier()));
        Object id3 = testSubject
                .getSequenceIdentifierFor(newStubDomainEvent(new UUIDAggregateIdentifier()));

        assertEquals(id1, id2);
        assertEquals(id2, id3);
        // this can only fail if equals is not implemented correctly
        assertEquals(id1, id3);
    }

    private DomainEventMessage newStubDomainEvent(AggregateIdentifier aggregateIdentifier) {
        return new GenericDomainEventMessage<Object>(aggregateIdentifier, (long) 0,
                                                     new Object(), MetaData.emptyInstance());
    }
}
