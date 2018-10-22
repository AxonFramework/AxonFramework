/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.junit.Test;

import java.util.stream.Stream;

import static junit.framework.TestCase.assertSame;
import static org.mockito.Mockito.mock;

public class EventUpcasterChainTest {

    @Test
    public void testCreateChainAndUpcast() {
        EventUpcasterChain eventUpcasterChain = new EventUpcasterChain(new SomeEventUpcaster(),
                                                                       new SomeOtherEventUpcaster());
        IntermediateEventRepresentation mockRepresentation = mock(IntermediateEventRepresentation.class);
        assertSame(mockRepresentation, eventUpcasterChain.upcast(Stream.of(mockRepresentation)).findFirst().get());
    }

    private static class SomeEventUpcaster extends SingleEventUpcaster {

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return true;
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation;
        }
    }

    private static class SomeOtherEventUpcaster extends SingleEventUpcaster {

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return true;
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation;
        }
    }

}
