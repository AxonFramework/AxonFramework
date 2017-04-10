package org.axonframework.serialization.upcasting.event;

import org.junit.Test;

import java.util.stream.Stream;

import static junit.framework.TestCase.assertSame;
import static org.mockito.Mockito.mock;

public class EventUpcasterChainTest {

    @Test
    public void testCreateChainAndUpcast() throws Exception {
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
