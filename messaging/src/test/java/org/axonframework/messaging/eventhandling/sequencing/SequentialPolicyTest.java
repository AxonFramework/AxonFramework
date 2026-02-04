/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.sequencing;

import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SequentialPolicy}.
 *
 * @author Allard Buijze
 */
class SequentialPolicyTest {

    @Test
    void sequencingIdentifier() {
        // ok, pretty useless, but everything should be tested
        SequentialPolicy testSubject = SequentialPolicy.INSTANCE;
        Object id1 = testSubject.getSequenceIdentifierFor(EventTestUtils.asEventMessage(UUID.randomUUID()),
                                                          new StubProcessingContext()).orElse(null);
        Object id2 = testSubject.getSequenceIdentifierFor(EventTestUtils.asEventMessage(UUID.randomUUID()),
                                                          new StubProcessingContext()).orElse(null);
        Object id3 = testSubject.getSequenceIdentifierFor(EventTestUtils.asEventMessage(UUID.randomUUID()),
                                                          new StubProcessingContext()).orElse(null);

        assertEquals(id1, id2);
        assertEquals(id2, id3);
        // this can only fail if equals is not implemented correctly
        assertEquals(id1, id3);
    }
}
