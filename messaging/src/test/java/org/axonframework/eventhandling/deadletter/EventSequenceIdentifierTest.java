package org.axonframework.eventhandling.deadletter;

import org.axonframework.messaging.deadletter.SequenceIdentifier;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventSequenceIdentifier}.
 *
 * @author Steven van Beelen
 */
class EventSequenceIdentifierTest {

    private static final Object SEQUENCE_IDENTIFIER = "sequenceId";
    private static final String PROCESSING_GROUP = "group";

    @Test
    void testConstruct() {
        EventSequenceIdentifier testSubject = new EventSequenceIdentifier(SEQUENCE_IDENTIFIER, PROCESSING_GROUP);

        assertEquals(SEQUENCE_IDENTIFIER, testSubject.identifier());
        assertEquals(PROCESSING_GROUP, testSubject.group());
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testSerializationOfGenericDeadLetter(TestSerializer serializer) {
        SequenceIdentifier testSubject = new EventSequenceIdentifier(SEQUENCE_IDENTIFIER, PROCESSING_GROUP);

        SequenceIdentifier result = serializer.serializeDeserialize(testSubject);

        assertEquals(testSubject, result);
    }

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}