package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AndCommandMessageFilter} can be serialized through Axon's {@link
 * org.axonframework.serialization.Serializer} implementations.
 *
 * @author Steven van Beelen
 */
class AndCommandMessageFilterSerializationTest {

    private final AndCommandMessageFilter testSubject =
            new AndCommandMessageFilter(new CommandNameFilter("firstName"), new CommandNameFilter("secondName"));

    private static Collection<TestSerializer> testSerializers() {
        return TestSerializer.all();
    }

    @ParameterizedTest
    @MethodSource("testSerializers")
    void andCommandMessageFilterShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject, serializer.serializeDeserialize(testSubject));
    }
}